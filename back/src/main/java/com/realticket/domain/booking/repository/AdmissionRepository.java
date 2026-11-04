package com.realticket.domain.booking.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 대기열(waiting)과 입장 허가(entering) 상태.
 * 인원 확인과 상태 변경이 섞인 동작은 Lua 로 원자적으로 처리한다
 * (Nest 버전은 확인과 변경 사이에 다른 요청이 끼면 정원보다 많이 입장시킬 수 있었다).
 */
@Repository
@RequiredArgsConstructor
public class AdmissionRepository {

    private final StringRedisTemplate redis;

    /** @return 0 바로 입장, -1 이미 좌석 선택 중, -2 이미 입장 허가됨, 양수 대기 번호 */
    public long tryEnter(long eventId, String sid, long nowMillis) {
        return redis.execute(BookingScripts.TRY_ENTER, List.of(
                        BookingKeys.waitingQueue(eventId),
                        BookingKeys.waitingOrder(eventId),
                        BookingKeys.waitingOrders(eventId),
                        BookingKeys.entering(eventId),
                        BookingKeys.inBookingSessions(eventId),
                        BookingKeys.maxSize(eventId)),
                sid, String.valueOf(nowMillis));
    }

    /** 빈자리만큼 대기열 앞에서부터 입장시키고, 입장시킨 sid 를 반환한다. */
    public List<String> admitWaiting(long eventId, long nowMillis) {
        return redis.execute(BookingScripts.ADMIT_WAITING, List.of(
                        BookingKeys.waitingQueue(eventId),
                        BookingKeys.waitingOrders(eventId),
                        BookingKeys.entering(eventId),
                        BookingKeys.inBookingSessions(eventId),
                        BookingKeys.maxSize(eventId)),
                String.valueOf(nowMillis));
    }

    /** cutoffMillis 이전에 입장 허가된 세션을 지우고 그 sid 를 반환한다. */
    public List<String> expireEntering(long eventId, long cutoffMillis) {
        return redis.execute(BookingScripts.EXPIRE_ENTERING, List.of(
                        BookingKeys.entering(eventId),
                        BookingKeys.enteringAmounts(eventId)),
                String.valueOf(cutoffMillis));
    }

    public boolean isEntering(long eventId, String sid) {
        return redis.opsForZSet().score(BookingKeys.entering(eventId), sid) != null;
    }

    public void setEnteringAmount(long eventId, String sid, int amount) {
        redis.opsForHash().put(BookingKeys.enteringAmounts(eventId), sid, String.valueOf(amount));
    }

    public long enteringCount(long eventId) {
        Long count = redis.opsForZSet().zCard(BookingKeys.entering(eventId));
        return count != null ? count : 0;
    }

    public long waitingCount(long eventId) {
        Long size = redis.opsForList().size(BookingKeys.waitingQueue(eventId));
        return size != null ? size : 0;
    }

    /**
     * 대기열 맨 앞 사용자의 대기 번호. 대기열이 비어 있으면 "마지막 번호 + 1".
     * 프런트엔드는 (내 번호 < headOrder) 가 되면 입장한 것으로 보고 좌석 화면으로 이동한다.
     */
    public long headOrder(long eventId) {
        String head = redis.opsForList().index(BookingKeys.waitingQueue(eventId), 0);
        if (head != null) {
            Object order = redis.opsForHash().get(BookingKeys.waitingOrders(eventId), head);
            if (order != null) {
                return Long.parseLong((String) order);
            }
        }
        String last = redis.opsForValue().get(BookingKeys.waitingOrder(eventId));
        return (last != null ? Long.parseLong(last) : 0) + 1;
    }

    public List<String> findWaitingSids(long eventId) {
        List<String> sids = redis.opsForList().range(BookingKeys.waitingQueue(eventId), 0, -1);
        return sids != null ? sids : List.of();
    }

    public Set<String> findEnteringSids(long eventId) {
        Set<String> sids = redis.opsForZSet().range(BookingKeys.entering(eventId), 0, -1);
        return sids != null ? sids : Set.of();
    }

    public void deleteAll(long eventId) {
        redis.unlink(List.of(
                BookingKeys.waitingQueue(eventId),
                BookingKeys.waitingOrder(eventId),
                BookingKeys.waitingOrders(eventId),
                BookingKeys.entering(eventId),
                BookingKeys.enteringAmounts(eventId)));
    }
}
