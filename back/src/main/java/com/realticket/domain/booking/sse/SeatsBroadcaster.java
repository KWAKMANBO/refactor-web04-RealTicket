package com.realticket.domain.booking.sse;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.realticket.domain.booking.config.BookingProperties;
import com.realticket.domain.booking.dto.SeatsSseResponse;
import com.realticket.domain.booking.event.SeatsChangedEvent;
import com.realticket.domain.booking.event.SeatsSubscriptionClosedEvent;
import com.realticket.domain.booking.repository.SeatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 실시간 좌석 현황 브로드캐스트.
 * <ul>
 *     <li>좌석이 바뀐 이벤트만 seatsBroadcastInterval(0.5초)마다 다시 보낸다.</li>
 *     <li>변경이 없어도 sseMaximumInterval(30초)마다 전체 상태를 보낸다.</li>
 *     <li>sseHeartbeatInterval(5초)마다 SSE 주석을 보내 끊긴 연결을 빨리 찾아낸다.
 *         연결이 끊기면 {@link SeatsSubscriptionClosedEvent} 를 발행해 선점 좌석을 풀어 준다.</li>
 * </ul>
 * 이벤트별 interval 을 따로 만들지 않고 전역 주기 하나로 처리하므로, 마감 후 interval 이 남는 문제가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatsBroadcaster {

    private final Map<Long, Channel> channels = new ConcurrentHashMap<>();

    private final SeatRepository seatRepository;
    private final SseSupport sseSupport;
    private final BookingProperties properties;
    private final TaskScheduler taskScheduler;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        taskScheduler.scheduleWithFixedDelay(this::broadcastChanged, properties.seatsBroadcastInterval());
        taskScheduler.scheduleWithFixedDelay(this::heartbeat, properties.sseHeartbeatInterval());
    }

    /** 구독을 시작하고 현재 좌석 상태를 바로 보낸다. */
    public SseEmitter subscribe(long eventId, String sid) {
        Channel channel = channels.computeIfAbsent(eventId, this::newChannel);
        SseEmitter emitter = channel.sse.subscribe(sid);

        Set<DataWithMediaType> current = readSeatsEvent(eventId);
        if (current != null) {
            channel.sse.send(sid, emitter, current);
        }
        return emitter;
    }

    @EventListener
    public void onSeatsChanged(SeatsChangedEvent event) {
        Channel channel = channels.get(event.eventId());
        if (channel != null) {
            channel.dirty.set(true);
        }
    }

    /** 예매 마감: 연결을 모두 닫는다 (떠남 처리는 하지 않음). */
    public void close(long eventId) {
        Channel channel = channels.remove(eventId);
        if (channel != null) {
            channel.sse.closeAll();
        }
    }

    public int subscriberCount(long eventId) {
        Channel channel = channels.get(eventId);
        return channel != null ? channel.sse.size() : 0;
    }

    void broadcastChanged() {
        long now = System.currentTimeMillis();
        channels.forEach((eventId, channel) -> {
            try {
                boolean expired = now - channel.lastBroadcastAt >= properties.sseMaximumInterval().toMillis();
                if (!channel.dirty.getAndSet(false) && !expired) {
                    return;
                }
                Set<DataWithMediaType> event = readSeatsEvent(eventId);
                if (event == null) {
                    return;
                }
                channel.lastBroadcastAt = now;
                channel.sse.broadcast(event);
            } catch (Exception e) {
                log.error("좌석 브로드캐스트 실패 eventId={}", eventId, e);
            }
        });
    }

    void heartbeat() {
        Set<DataWithMediaType> heartbeat = sseSupport.heartbeat();
        channels.values().forEach(channel -> channel.sse.broadcast(heartbeat));
    }

    private Set<DataWithMediaType> readSeatsEvent(long eventId) {
        boolean[][] seats = seatRepository.findAll(eventId);
        return seats == null ? null : sseSupport.dataEvent(new SeatsSseResponse(seats), properties.seatsSseRetry());
    }

    private Channel newChannel(long eventId) {
        return new Channel(new SseChannel(sseSupport.sendExecutor(),
                (sid, emitter) -> eventPublisher.publishEvent(new SeatsSubscriptionClosedEvent(eventId, sid))));
    }

    private static final class Channel {
        final SseChannel sse;
        final AtomicBoolean dirty = new AtomicBoolean(false);
        volatile long lastBroadcastAt = System.currentTimeMillis();

        Channel(SseChannel sse) {
            this.sse = sse;
        }
    }
}
