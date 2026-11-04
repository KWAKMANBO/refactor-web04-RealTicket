package com.realticket.domain.booking.sse;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/**
 * 한 이벤트의 SSE 구독자 묶음. 사용자(sid)당 연결은 하나만 유지한다.
 * 같은 sid 로 다시 연결하면 이전 연결을 닫고, 이전 연결의 종료는 "떠남"으로 처리하지 않는다.
 */
@Slf4j
class SseChannel {

    private final Map<String, SseEmitter> subscribers = new ConcurrentHashMap<>();
    private final Executor sendExecutor;
    private final BiConsumer<String, SseEmitter> onDisconnect;

    /**
     * @param onDisconnect 연결이 끊겼을 때(타임아웃, 오류, 전송 실패 포함) 한 번 호출된다.
     *                     다른 연결로 교체되었거나 채널이 닫힌 경우에는 호출되지 않는다.
     */
    SseChannel(Executor sendExecutor, BiConsumer<String, SseEmitter> onDisconnect) {
        this.sendExecutor = sendExecutor;
        this.onDisconnect = onDisconnect;
    }

    SseEmitter subscribe(String sid) {
        SseEmitter emitter = new SseEmitter(0L); // 시간 제한 없음. 끊긴 연결은 heartbeat 전송 실패로 감지한다.
        emitter.onCompletion(() -> disconnected(sid, emitter));
        emitter.onTimeout(() -> disconnected(sid, emitter));
        emitter.onError(e -> disconnected(sid, emitter));

        SseEmitter previous = subscribers.put(sid, emitter);
        if (previous != null) {
            previous.complete();
        }
        return emitter;
    }

    boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    int size() {
        return subscribers.size();
    }

    /** 한 번 만든 이벤트 데이터를 모든 구독자에게 보낸다 (구독자 수와 상관없이 직렬화는 한 번). */
    void broadcast(Set<DataWithMediaType> event) {
        subscribers.forEach((sid, emitter) -> sendExecutor.execute(() -> send(sid, emitter, event)));
    }

    void send(String sid, SseEmitter emitter, Set<DataWithMediaType> event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 전송 실패 sid={}: {}", sid, e.getMessage());
            disconnected(sid, emitter);
            completeQuietly(emitter);
        }
    }

    /** 채널을 닫는다. 이때의 연결 종료는 "떠남"으로 처리하지 않는다. */
    void closeAll() {
        Map<String, SseEmitter> snapshot = Map.copyOf(subscribers);
        subscribers.clear();
        snapshot.values().forEach(SseChannel::completeQuietly);
    }

    private void disconnected(String sid, SseEmitter emitter) {
        if (subscribers.remove(sid, emitter)) {
            onDisconnect.accept(sid, emitter);
        }
    }

    private static void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 이미 끝난 연결
        }
    }
}
