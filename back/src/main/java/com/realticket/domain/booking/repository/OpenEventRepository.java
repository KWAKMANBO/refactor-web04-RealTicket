package com.realticket.domain.booking.repository;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** 오픈된 이벤트 목록과 좌석 선택 화면 최대 인원 */
@Repository
@RequiredArgsConstructor
public class OpenEventRepository {

    private final StringRedisTemplate redis;

    public boolean isOpened(long eventId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(BookingKeys.OPENED_EVENTS, String.valueOf(eventId)));
    }

    public Set<Long> findOpenedEventIds() {
        Set<String> members = redis.opsForSet().members(BookingKeys.OPENED_EVENTS);
        return members == null ? Set.of()
                : members.stream().map(Long::parseLong).collect(Collectors.toUnmodifiableSet());
    }

    public void markOpened(long eventId) {
        redis.opsForSet().add(BookingKeys.OPENED_EVENTS, String.valueOf(eventId));
    }

    public void unmarkOpened(long eventId) {
        redis.opsForSet().remove(BookingKeys.OPENED_EVENTS, String.valueOf(eventId));
    }

    public int getDefaultMaxSize(int fallback) {
        String value = redis.opsForValue().get(BookingKeys.DEFAULT_MAX_SIZE);
        return value != null ? Integer.parseInt(value) : fallback;
    }

    public void setDefaultMaxSize(int size) {
        redis.opsForValue().set(BookingKeys.DEFAULT_MAX_SIZE, String.valueOf(size));
    }

    public int getMaxSize(long eventId) {
        String value = redis.opsForValue().get(BookingKeys.maxSize(eventId));
        return value != null ? Integer.parseInt(value) : 0;
    }

    public void setMaxSize(long eventId, int size) {
        redis.opsForValue().set(BookingKeys.maxSize(eventId), String.valueOf(size));
    }

    public void deleteMaxSize(long eventId) {
        redis.unlink(BookingKeys.maxSize(eventId));
    }
}
