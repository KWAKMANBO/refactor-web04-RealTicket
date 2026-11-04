package com.realticket.global.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 세션 저장소 (Redis).
 * <pre>
 * user:{sid}          Hash  { userId, loginId, status, targetEventId? }
 * user-id:{loginId}   String  sid   (계정당 세션 1개 유지용)
 * </pre>
 * Nest 버전은 세션을 JSON 문자열로 저장해 "읽기 → 수정 → 쓰기" 사이에 다른 요청이 끼면 값이 덮어써졌다.
 * Hash 필드 단위로 갱신하고, 조건부 갱신은 Lua 로 원자적으로 처리한다.
 * 또한 세션이 이미 만료된 경우 갱신하지 않는다 (Nest 버전은 TTL 없는 세션을 다시 만들었다).
 */
@Repository
@RequiredArgsConstructor
public class SessionRepository {

    private static final String F_USER_ID = "userId";
    private static final String F_LOGIN_ID = "loginId";
    private static final String F_STATUS = "status";
    private static final String F_TARGET_EVENT = "targetEventId";

    /** 세션이 존재하고 ADMIN 이 아닐 때만 status 갱신. 갱신 시 1 반환 */
    private static final RedisScript<Long> UPDATE_STATUS_UNLESS_ADMIN = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'status') == 'ADMIN' then return 0 end
            redis.call('HSET', KEYS[1], 'status', ARGV[1])
            return 1
            """, Long.class);

    /** 세션이 존재할 때만 필드 갱신. ARGV[2] 가 빈 문자열이면 필드 삭제 */
    private static final RedisScript<Long> SET_FIELD_IF_EXISTS = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if ARGV[2] == '' then
              redis.call('HDEL', KEYS[1], ARGV[1])
            else
              redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final SessionProperties properties;

    /** 새 세션을 만든다. 같은 계정의 기존 세션은 삭제한다 (중복 로그인 방지). */
    public SessionUser create(long userId, String loginId, UserStatus status) {
        String oldSid = redis.opsForValue().get(loginKey(loginId));
        if (oldSid != null) {
            redis.unlink(sessionKey(oldSid));
        }

        String sid = UUID.randomUUID().toString();
        redis.opsForHash().putAll(sessionKey(sid), Map.of(
                F_USER_ID, String.valueOf(userId),
                F_LOGIN_ID, loginId,
                F_STATUS, status.name()));
        redis.expire(sessionKey(sid), properties.ttl());
        redis.opsForValue().set(loginKey(loginId), sid, properties.ttl());

        return new SessionUser(sid, userId, loginId, status, null);
    }

    public Optional<SessionUser> findBySid(String sid) {
        if (sid == null) {
            return Optional.empty();
        }
        Map<Object, Object> hash = redis.opsForHash().entries(sessionKey(sid));
        if (hash.isEmpty() || hash.get(F_USER_ID) == null || hash.get(F_LOGIN_ID) == null) {
            return Optional.empty();
        }
        Object target = hash.get(F_TARGET_EVENT);
        return Optional.of(new SessionUser(
                sid,
                Long.parseLong((String) hash.get(F_USER_ID)),
                (String) hash.get(F_LOGIN_ID),
                UserStatus.valueOf((String) hash.get(F_STATUS)),
                target != null ? Long.parseLong((String) target) : null));
    }

    /** ADMIN 세션은 바꾸지 않는다. */
    public boolean updateStatus(String sid, UserStatus status) {
        Long result = redis.execute(UPDATE_STATUS_UNLESS_ADMIN, List.of(sessionKey(sid)), status.name());
        return result != null && result == 1L;
    }

    /** eventId 가 null 이면 예매 대상 이벤트를 비운다. */
    public boolean updateTargetEvent(String sid, Long eventId) {
        String value = eventId != null ? String.valueOf(eventId) : "";
        Long result = redis.execute(SET_FIELD_IF_EXISTS, List.of(sessionKey(sid)), F_TARGET_EVENT, value);
        return result != null && result == 1L;
    }

    /** 세션 만료 시간을 연장한다 (sliding expiration). */
    public void touch(SessionUser session) {
        redis.expire(sessionKey(session.sid()), properties.ttl());
        redis.expire(loginKey(session.loginId()), properties.ttl());
    }

    public boolean delete(String sid, String loginId) {
        redis.unlink(loginKey(loginId));
        Boolean deleted = redis.unlink(sessionKey(sid));
        return Boolean.TRUE.equals(deleted);
    }

    private static String sessionKey(String sid) {
        return "user:" + sid;
    }

    private static String loginKey(String loginId) {
        return "user-id:" + loginId;
    }
}
