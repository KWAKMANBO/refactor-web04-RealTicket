package com.realticket.domain.booking.service;

import java.time.Clock;
import java.util.Collection;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.realticket.domain.booking.config.BookingProperties;
import com.realticket.domain.booking.dto.AdmissionResponse;
import com.realticket.domain.booking.dto.SeatResponse;
import com.realticket.domain.booking.dto.SeatStatus;
import com.realticket.domain.booking.event.SeatsChangedEvent;
import com.realticket.domain.booking.event.SeatsSubscriptionClosedEvent;
import com.realticket.domain.booking.repository.AdmissionRepository;
import com.realticket.domain.booking.repository.InBookingRepository;
import com.realticket.domain.booking.repository.OpenEventRepository;
import com.realticket.domain.booking.repository.SeatRepository;
import com.realticket.domain.booking.sse.SeatsBroadcaster;
import com.realticket.domain.booking.sse.WaitingBroadcaster;
import com.realticket.global.auth.SessionRepository;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.auth.UserStatus;
import com.realticket.global.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예매 진행 흐름.
 * <pre>
 * LOGIN ──입장 요청──▶ ENTERING (자리 있음) ──좌석 SSE 연결──▶ SELECTING_SEAT ──SSE 끊김──▶ LOGIN
 *            └───────▶ WAITING (대기열) ──빈자리 생김──▶ ENTERING
 * ENTERING 상태로 enteringSessionExpiry 동안 좌석 화면에 오지 않으면 LOGIN 으로 돌아간다.
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final OpenEventRepository openEventRepository;
    private final AdmissionRepository admissionRepository;
    private final InBookingRepository inBookingRepository;
    private final SeatRepository seatRepository;
    private final SessionRepository sessionRepository;
    private final SeatsBroadcaster seatsBroadcaster;
    private final WaitingBroadcaster waitingBroadcaster;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingProperties properties;
    private final Clock clock;

    /** 예매 입장 요청. 자리가 있으면 바로 입장, 없으면 대기열에 넣는다. */
    public AdmissionResponse requestAdmission(SessionUser user, long eventId) {
        requireOpened(eventId);
        sessionRepository.updateTargetEvent(user.sid(), eventId);

        long result = admissionRepository.tryEnter(eventId, user.sid(), clock.millis());
        if (result == -1) {
            return AdmissionResponse.entering(); // 이미 좌석 선택 중
        }
        if (result <= 0) {
            sessionRepository.updateStatus(user.sid(), UserStatus.ENTERING);
            return AdmissionResponse.entering();
        }
        sessionRepository.updateStatus(user.sid(), UserStatus.WAITING);
        return AdmissionResponse.waiting(result);
    }

    public SseEmitter subscribeWaiting(SessionUser user, long eventId) {
        requireTarget(user, eventId);
        return waitingBroadcaster.subscribe(eventId, user.sid());
    }

    /** 좌석 선택 화면 진입: 입장 허가 → 좌석 선택 중으로 옮기고 좌석 SSE 구독을 시작한다. */
    public SseEmitter enterSeatSelection(SessionUser user, long eventId) {
        requireTarget(user, eventId);
        requireOpened(eventId);
        if (inBookingRepository.moveFromEntering(eventId, user.sid()) < 0) {
            throw ApiException.unauthorized("입장 가능 시간이 지났습니다. 다시 예매를 시도해 주세요.");
        }
        sessionRepository.updateStatus(user.sid(), UserStatus.SELECTING_SEAT);
        return seatsBroadcaster.subscribe(eventId, user.sid());
    }

    /** 예매 인원 설정. 좌석 선택 중이면 이미 선점한 좌석을 모두 풀어 준다. */
    public int setBookingAmount(SessionUser user, int amount) {
        long eventId = requireTarget(user);
        if (amount < 1 || amount > properties.maxBookingAmount()) {
            throw ApiException.badRequest("예매 인원은 1~" + properties.maxBookingAmount() + "명입니다.");
        }
        if (inBookingRepository.isInBooking(eventId, user.sid())) {
            long released = inBookingRepository.resetBookingAmount(eventId, user.sid(), amount);
            if (released == -2) {
                throw ApiException.badRequest("이미 예매가 완료되었습니다.");
            }
            if (released > 0) {
                seatsChanged(eventId);
            }
            return amount;
        }
        admissionRepository.setEnteringAmount(eventId, user.sid(), amount);
        return amount;
    }

    public SeatResponse updateSeat(SessionUser user, long requestEventId, Seat seat, SeatStatus expected) {
        long eventId = requireTarget(user, requestEventId);
        if (expected == SeatStatus.RESERVE) {
            long result = inBookingRepository.reserveSeat(eventId, user.sid(), seat);
            switch ((int) result) {
                case 1 -> seatsChanged(eventId);
                case 0 -> throw ApiException.conflict("이미 예약된 좌석입니다.");
                case -2 -> throw ApiException.badRequest("이미 예매가 완료되었습니다.");
                case -3 -> throw ApiException.badRequest("예약 가능한 좌석 수를 초과했습니다.");
                case -4 -> throw ApiException.badRequest("좌석이 존재하지 않습니다.");
                default -> throw ApiException.badRequest("좌석 선택 중이 아닙니다.");
            }
        } else {
            long result = inBookingRepository.releaseSeat(eventId, user.sid(), seat);
            switch ((int) result) {
                case 1 -> seatsChanged(eventId);
                case -2 -> throw ApiException.badRequest("이미 예매가 완료되었습니다.");
                case -5 -> throw ApiException.badRequest("예약하지 않은 좌석입니다.");
                default -> throw ApiException.badRequest("좌석 선택 중이 아닙니다.");
            }
        }
        return new SeatResponse(eventId, seat.sectionIndex(), seat.seatIndex(), expected);
    }

    /**
     * 좌석 SSE 가 끊김 = 좌석 선택 화면을 떠남.
     * 예매를 완료하지 않았다면 선점 좌석을 풀고, 상태를 LOGIN 으로 돌린 뒤 대기자를 입장시킨다.
     */
    @Async
    @EventListener
    public void onSeatsSubscriptionClosed(SeatsSubscriptionClosedEvent event) {
        leaveSeatSelection(event.eventId(), event.sid());
    }

    public void leaveSeatSelection(long eventId, String sid) {
        if (!openEventRepository.isOpened(eventId)) {
            return;
        }
        long released = inBookingRepository.leave(eventId, sid);
        if (released < 0) {
            return;
        }
        if (released > 0) {
            seatsChanged(eventId);
        }
        sessionRepository.updateStatus(sid, UserStatus.LOGIN);
        sessionRepository.updateTargetEvent(sid, null);
        admitWaiting(eventId);
    }

    /** 빈자리만큼 대기자를 입장시킨다. */
    public void admitWaiting(long eventId) {
        List<String> admitted = admissionRepository.admitWaiting(eventId, clock.millis());
        admitted.forEach(sid -> sessionRepository.updateStatus(sid, UserStatus.ENTERING));
    }

    /** 입장 허가 후 좌석 화면에 오지 않은 사용자를 정리하고 대기자를 입장시킨다. */
    public void collectExpiredEntering() {
        long cutoff = clock.millis() - properties.enteringSessionExpiry().toMillis();
        for (long eventId : openEventRepository.findOpenedEventIds()) {
            try {
                List<String> expired = admissionRepository.expireEntering(eventId, cutoff);
                expired.forEach(sid -> {
                    sessionRepository.updateStatus(sid, UserStatus.LOGIN);
                    sessionRepository.updateTargetEvent(sid, null);
                });
                admitWaiting(eventId);
            } catch (Exception e) {
                log.error("입장 세션 정리 실패 eventId={}", eventId, e);
            }
        }
    }

    // ---------- 예매 확정(reservation)에서 사용 ----------

    public InBookingSnapshot getInBooking(long eventId, String sid) {
        if (!inBookingRepository.isInBooking(eventId, sid)) {
            throw ApiException.badRequest("좌석 선택 중이 아닙니다.");
        }
        return new InBookingSnapshot(
                inBookingRepository.getBookingAmount(eventId, sid),
                inBookingRepository.findBookedSeats(eventId, sid));
    }

    /** 예매 저장을 시작한다. 같은 좌석으로 두 번 예매되는 것을 막는다. */
    public void markSaving(long eventId, String sid) {
        long result = inBookingRepository.markSaved(eventId, sid);
        if (result == 0) {
            throw ApiException.badRequest("이미 예매가 완료되었습니다.");
        }
        if (result < 0) {
            throw ApiException.badRequest("좌석 선택 중이 아닙니다.");
        }
    }

    public void cancelSaving(long eventId, String sid) {
        inBookingRepository.unmarkSaved(eventId, sid);
    }

    /** 취소된 예매의 좌석을 다시 예약 가능하게 만든다 (예매가 열려 있는 이벤트만). */
    public void freeSeats(long eventId, Collection<Seat> seats) {
        if (seats.isEmpty() || !openEventRepository.isOpened(eventId)) {
            return;
        }
        seatRepository.free(eventId, seats);
        seatsChanged(eventId);
    }

    // ---------- 관리자 ----------

    public int setMaxSize(long eventId, int size) {
        openEventRepository.setMaxSize(eventId, size);
        admitWaiting(eventId);
        return size;
    }

    public int setMaxSizeOfAllOpened(int size) {
        for (long eventId : openEventRepository.findOpenedEventIds()) {
            setMaxSize(eventId, size);
        }
        return size;
    }

    public int setDefaultMaxSize(int size) {
        openEventRepository.setDefaultMaxSize(size);
        return size;
    }

    private void seatsChanged(long eventId) {
        eventPublisher.publishEvent(new SeatsChangedEvent(eventId));
    }

    private void requireOpened(long eventId) {
        if (!openEventRepository.isOpened(eventId)) {
            throw ApiException.badRequest("예약이 오픈되지 않았습니다.");
        }
    }

    private long requireTarget(SessionUser user) {
        if (user.targetEventId() == null) {
            throw ApiException.badRequest("예매 중인 이벤트가 없습니다.");
        }
        return user.targetEventId();
    }

    private long requireTarget(SessionUser user, long eventId) {
        long target = requireTarget(user);
        if (target != eventId) {
            throw ApiException.badRequest("예매 중인 이벤트가 아닙니다.");
        }
        return target;
    }
}
