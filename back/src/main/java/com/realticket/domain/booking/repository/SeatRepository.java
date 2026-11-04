package com.realticket.domain.booking.repository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.realticket.domain.booking.service.Seat;

import lombok.RequiredArgsConstructor;

/**
 * 이벤트별 좌석 상태 비트맵. 구역마다 비트 하나가 좌석 하나 (1: 예약 가능, 0: 예약됨/좌석 없음).
 */
@Repository
@RequiredArgsConstructor
public class SeatRepository {

    private final StringRedisTemplate redis;

    /** @param sections 구역 순서대로, 좌석별 예약 가능 여부 */
    public void init(long eventId, List<List<Boolean>> sections) {
        for (int i = 0; i < sections.size(); i++) {
            StringBuilder bits = new StringBuilder(sections.get(i).size());
            sections.get(i).forEach(available -> bits.append(Boolean.TRUE.equals(available) ? '1' : '0'));
            redis.execute(BookingScripts.INIT_SECTION,
                    List.of(BookingKeys.sectionSeats(eventId, i), BookingKeys.sectionSeatsLen(eventId, i)),
                    bits.toString());
        }
        redis.opsForValue().set(BookingKeys.sectionsLen(eventId), String.valueOf(sections.size()));
    }

    /**
     * 전체 좌석 상태. 구역별 비트맵을 한 번에(파이프라인) 읽어 Java 에서 풀어낸다.
     * (Nest 버전은 Lua 안에서 좌석마다 GETBIT 를 호출해 좌석 수만큼 Redis 를 붙잡고 있었다)
     *
     * @return 오픈되지 않은 이벤트면 null
     */
    public boolean[][] findAll(long eventId) {
        String sectionsLen = redis.opsForValue().get(BookingKeys.sectionsLen(eventId));
        if (sectionsLen == null) {
            return null;
        }
        int sectionCount = Integer.parseInt(sectionsLen);
        // 비트맵은 바이너리라 문자열 변환을 거치지 않도록 raw 커넥션으로 읽는다
        List<Object> results;
        try (RedisConnection connection = redis.getRequiredConnectionFactory().getConnection()) {
            connection.openPipeline();
            for (int i = 0; i < sectionCount; i++) {
                get(connection, BookingKeys.sectionSeatsLen(eventId, i));
                get(connection, BookingKeys.sectionSeats(eventId, i));
            }
            results = connection.closePipeline();
        }

        boolean[][] seats = new boolean[sectionCount][];
        for (int i = 0; i < sectionCount; i++) {
            byte[] lenBytes = (byte[]) results.get(i * 2);
            byte[] bitmap = (byte[]) results.get(i * 2 + 1);
            int len = lenBytes != null ? Integer.parseInt(new String(lenBytes, StandardCharsets.UTF_8)) : 0;
            seats[i] = new boolean[len];
            for (int j = 0; j < len; j++) {
                seats[i][j] = bitmap != null && j / 8 < bitmap.length
                        && ((bitmap[j / 8] >> (7 - j % 8)) & 1) == 1;
            }
        }
        return seats;
    }

    /** 예약 취소된 좌석을 다시 예약 가능 상태로 돌린다. */
    public void free(long eventId, Collection<Seat> seats) {
        for (Seat seat : seats) {
            redis.opsForValue().setBit(BookingKeys.sectionSeats(eventId, seat.sectionIndex()), seat.seatIndex(), true);
        }
    }

    public void deleteAll(long eventId) {
        String sectionsLen = redis.opsForValue().get(BookingKeys.sectionsLen(eventId));
        List<String> keys = new ArrayList<>();
        if (sectionsLen != null) {
            for (int i = 0; i < Integer.parseInt(sectionsLen); i++) {
                keys.add(BookingKeys.sectionSeats(eventId, i));
                keys.add(BookingKeys.sectionSeatsLen(eventId, i));
            }
        }
        keys.add(BookingKeys.sectionsLen(eventId));
        redis.unlink(keys);
    }

    private static void get(RedisConnection connection, String key) {
        connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8));
    }
}
