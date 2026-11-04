package com.realticket.domain.booking.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.realticket.domain.booking.config.BookingProperties;
import com.realticket.domain.booking.repository.AdmissionRepository;
import com.realticket.domain.booking.repository.InBookingRepository;
import com.realticket.domain.booking.repository.OpenEventRepository;
import com.realticket.domain.booking.repository.SeatRepository;
import com.realticket.domain.booking.sse.SeatsBroadcaster;
import com.realticket.domain.booking.sse.WaitingBroadcaster;
import com.realticket.domain.event.entity.Event;
import com.realticket.domain.event.repository.EventRepository;
import com.realticket.domain.place.entity.Section;
import com.realticket.domain.place.service.PlaceService;
import com.realticket.global.auth.SessionRepository;
import com.realticket.global.auth.UserStatus;
import com.realticket.global.config.TimeConfig;
import com.realticket.global.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예매 오픈/마감.
 * <ul>
 *     <li>서버 시작 시, 매시 59분, 관리자 요청 시 예약 마감 전인 이벤트를 확인한다.</li>
 *     <li>오픈 시각이 지난 이벤트는 바로 열고, 아직이면 오픈 시각에 열도록 예약한다.</li>
 *     <li>마감 시각에 대기열/입장/좌석 선택 상태와 좌석 정보를 모두 지운다.</li>
 * </ul>
 * 단일 서버를 전제로 한다. 서버를 여러 대로 늘리면 ShedLock 같은 분산 락이 필요하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenBookingService {

    private final Map<String, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();

    private final EventRepository eventRepository;
    private final PlaceService placeService;
    private final OpenEventRepository openEventRepository;
    private final AdmissionRepository admissionRepository;
    private final InBookingRepository inBookingRepository;
    private final SeatRepository seatRepository;
    private final SessionRepository sessionRepository;
    private final SeatsBroadcaster seatsBroadcaster;
    private final WaitingBroadcaster waitingBroadcaster;
    private final TaskScheduler taskScheduler;
    private final BookingProperties properties;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        openEventRepository.setDefaultMaxSize(properties.inBookingDefaultMaxSize());
        scheduleUpcomingReservations();
    }

    @Scheduled(cron = "0 59 * * * *", zone = "Asia/Seoul")
    public void scheduleUpcomingReservations() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            Set<Long> opened = openEventRepository.findOpenedEventIds();
            for (Event event : eventRepository.findUpcoming(now)) {
                if (!opened.contains(event.getId())) {
                    if (event.getReservationOpenDate().isAfter(now)) {
                        scheduleOpen(event);
                    } else {
                        open(event.getId());
                    }
                }
                scheduleClose(event);
            }
        } catch (Exception e) {
            log.error("예매 오픈/마감 예약 실패", e);
        }
    }

    /** 예매를 연다. 오픈 시각 전이면 그 시각에 열도록 예약만 한다. 이미 열려 있으면 아무것도 하지 않는다. */
    public void open(long eventId) {
        Event event = eventRepository.findWithProgramAndPlaceById(eventId)
                .orElseThrow(() -> ApiException.notFound("해당 이벤트[" + eventId + "]는 존재하지 않습니다."));
        if (event.getReservationOpenDate().isAfter(LocalDateTime.now(clock))) {
            scheduleOpen(event);
            return;
        }
        if (openEventRepository.isOpened(eventId)) {
            return;
        }

        List<List<Boolean>> seats = placeService.findSectionsInOrder(event.getPlace()).stream()
                .map(Section::getSeats)
                .toList();
        seatRepository.init(eventId, seats);
        openEventRepository.setMaxSize(eventId,
                openEventRepository.getDefaultMaxSize(properties.inBookingDefaultMaxSize()));
        openEventRepository.markOpened(eventId);
        log.info("예매 오픈 eventId={}", eventId);
    }

    /** 마감 시각이 지났으면 예매를 닫는다. 아직이면 마감 시각에 닫도록 다시 예약한다. */
    public void close(long eventId) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event != null && event.getReservationCloseDate().isAfter(LocalDateTime.now(clock))) {
            scheduleClose(event);
            return;
        }
        forceClose(eventId);
    }

    /** 시간과 상관없이 예매를 닫고, 진행 중이던 사용자를 모두 LOGIN 상태로 돌린다. */
    public void forceClose(long eventId) {
        openEventRepository.unmarkOpened(eventId);

        resetUsers(eventId, admissionRepository.findWaitingSids(eventId));
        resetUsers(eventId, admissionRepository.findEnteringSids(eventId));
        resetUsers(eventId, inBookingRepository.findSids(eventId));

        seatsBroadcaster.close(eventId);
        waitingBroadcaster.close(eventId);
        admissionRepository.deleteAll(eventId);
        inBookingRepository.deleteAll(eventId);
        seatRepository.deleteAll(eventId);
        openEventRepository.deleteMaxSize(eventId);
        log.info("예매 마감 eventId={}", eventId);
    }

    /** 관리자: 예매 상태를 처음으로 되돌린다 (닫고 다시 열기). */
    public void reset(long eventId) {
        forceClose(eventId);
        open(eventId);
    }

    private void resetUsers(long eventId, Collection<String> sids) {
        for (String sid : sids) {
            sessionRepository.findBySid(sid)
                    .filter(session -> session.targetEventId() == null || session.targetEventId() == eventId)
                    .ifPresent(session -> {
                        sessionRepository.updateStatus(sid, UserStatus.LOGIN);
                        sessionRepository.updateTargetEvent(sid, null);
                    });
        }
    }

    private void scheduleOpen(Event event) {
        long eventId = event.getId();
        schedule("open-" + eventId, event.getReservationOpenDate(), () -> open(eventId));
    }

    private void scheduleClose(Event event) {
        long eventId = event.getId();
        schedule("close-" + eventId, event.getReservationCloseDate(), () -> close(eventId));
    }

    private void schedule(String name, LocalDateTime at, Runnable task) {
        ScheduledFuture<?> previous = scheduledJobs.remove(name);
        if (previous != null) {
            previous.cancel(false);
        }
        Runnable safeTask = () -> {
            scheduledJobs.remove(name);
            try {
                task.run();
            } catch (Exception e) {
                log.error("예약 작업 실패 {}", name, e);
            }
        };
        scheduledJobs.put(name, taskScheduler.schedule(safeTask, TimeConfig.toInstant(at)));
    }
}
