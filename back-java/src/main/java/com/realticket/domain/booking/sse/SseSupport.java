package com.realticket.domain.booking.sse;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/** SSE 이벤트 생성과 전송용 스레드. 느린 클라이언트 하나가 전체 전송을 붙잡지 않도록 가상 스레드로 보낸다. */
@Component
@RequiredArgsConstructor
class SseSupport {

    private final ObjectMapper objectMapper;
    private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

    ExecutorService sendExecutor() {
        return sendExecutor;
    }

    Set<DataWithMediaType> dataEvent(Object payload, Duration retry) {
        SseEmitter.SseEventBuilder builder = SseEmitter.event().data(toJson(payload));
        if (retry != null) {
            builder.reconnectTime(retry.toMillis());
        }
        return builder.build();
    }

    Set<DataWithMediaType> heartbeat() {
        return SseEmitter.event().comment("").build();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @PreDestroy
    void shutdown() {
        sendExecutor.shutdownNow();
    }
}
