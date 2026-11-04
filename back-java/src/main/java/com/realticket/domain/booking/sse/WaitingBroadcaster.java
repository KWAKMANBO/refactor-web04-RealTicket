package com.realticket.domain.booking.sse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.realticket.domain.booking.config.BookingProperties;
import com.realticket.domain.booking.dto.WaitingSseResponse;
import com.realticket.domain.booking.repository.AdmissionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 대기열 현황(맨 앞 대기 번호, 대기 인원)을 waitingBroadcastInterval(3초)마다 보낸다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingBroadcaster {

    private final Map<Long, SseChannel> channels = new ConcurrentHashMap<>();

    private final AdmissionRepository admissionRepository;
    private final SseSupport sseSupport;
    private final BookingProperties properties;
    private final TaskScheduler taskScheduler;

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        taskScheduler.scheduleWithFixedDelay(this::broadcast, properties.waitingBroadcastInterval());
    }

    /** 구독을 시작하고 현재 대기 현황을 바로 보낸다. */
    public SseEmitter subscribe(long eventId, String sid) {
        SseChannel channel = channels.computeIfAbsent(eventId,
                id -> new SseChannel(sseSupport.sendExecutor(), (s, emitter) -> { }));
        SseEmitter emitter = channel.subscribe(sid);
        channel.send(sid, emitter, sseSupport.dataEvent(situation(eventId), null));
        return emitter;
    }

    public void close(long eventId) {
        SseChannel channel = channels.remove(eventId);
        if (channel != null) {
            channel.closeAll();
        }
    }

    void broadcast() {
        channels.forEach((eventId, channel) -> {
            if (!channel.hasSubscribers()) {
                return;
            }
            try {
                channel.broadcast(sseSupport.dataEvent(situation(eventId), null));
            } catch (Exception e) {
                log.error("대기열 브로드캐스트 실패 eventId={}", eventId, e);
            }
        });
    }

    private WaitingSseResponse situation(long eventId) {
        return new WaitingSseResponse(
                admissionRepository.headOrder(eventId),
                admissionRepository.waitingCount(eventId),
                properties.waitingThroughputRate());
    }
}
