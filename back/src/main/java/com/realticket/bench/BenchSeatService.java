package com.realticket.bench;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.realticket.domain.booking.repository.BookingKeys;
import com.realticket.domain.booking.service.OpenBookingService;
import com.realticket.global.exception.ApiException;

import lombok.RequiredArgsConstructor;

/**
 * [비교 2] 좌석 선점 방식별 구현과 중복 예매 집계.
 * <p>
 * 측정 대상을 "좌석 확인 + 예약"으로 좁히기 위해 로그인 세션·인원 수 확인은 하지 않는다.
 * (운영 경로 POST /booking 은 여기에 세션 조회와 인원 수 확인이 더해진다)
 * <p>
 * 중복 예매 판정: 좌석을 누군가 잡고 있는 동안 또 다른 요청이 "성공"하면 중복 예매 1건.
 */
@Profile("bench")
@Service
@RequiredArgsConstructor
public class BenchSeatService {

    /** 좌석이 비어 있으면(1) 0으로 바꾸고 1, 이미 예약(0)이면 0, 범위 밖이면 -1 */
    private static final RedisScript<Long> RESERVE_LUA = new DefaultRedisScript<>("""
            local len = tonumber(redis.call('GET', KEYS[2]) or '-1')
            local index = tonumber(ARGV[1])
            if index < 0 or index >= len then return -1 end
            if redis.call('GETBIT', KEYS[1], index) == 0 then return 0 end
            redis.call('SETBIT', KEYS[1], index, 0)
            return 1
            """, Long.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redis;
    private final OpenBookingService openBookingService;

    /** 좌석별 현재 점유 수 (정상이면 0 또는 1) */
    private final Map<String, AtomicInteger> holders = new ConcurrentHashMap<>();
    private final Map<SeatLockStrategy, Counters> counters = new EnumMap<>(SeatLockStrategy.class);

    {
        for (SeatLockStrategy strategy : SeatLockStrategy.values()) {
            counters.put(strategy, new Counters());
        }
    }

    public record SeatTarget(long eventId, int sectionIndex, int seatIndex, String userId) {
    }

    /** @return true 예약 성공, false 이미 예약된 좌석 */
    public boolean reserve(SeatLockStrategy strategy, SeatTarget target) {
        boolean success = switch (strategy) {
            case DB_NAIVE -> dbNaive(target);
            case DB_LOCK -> dbLock(target);
            case DB_CONDITIONAL -> dbConditional(target);
            case REDIS_NAIVE -> redisNaive(target);
            case REDIS_LUA -> redisLua(target);
        };
        Counters counter = counters.get(strategy);
        if (success) {
            counter.successes.incrementAndGet();
            int current = holders.computeIfAbsent(key(strategy, target), k -> new AtomicInteger()).incrementAndGet();
            if (current > 1) {
                counter.doubleBookings.incrementAndGet();
            }
        } else {
            counter.conflicts.incrementAndGet();
        }
        return success;
    }

    /** 좌석을 다시 비운다 (처리량 측정에서 좌석을 순환시키기 위함) */
    public void release(SeatLockStrategy strategy, SeatTarget target) {
        AtomicInteger holder = holders.get(key(strategy, target));
        if (holder != null && holder.get() > 0) {
            holder.decrementAndGet();
        }
        if (strategy.name().startsWith("DB")) {
            jdbcTemplate.update("""
                    UPDATE bench_event_seat_status SET reserved = FALSE, reserved_by = NULL
                    WHERE event_id = ? AND section_index = ? AND seat_index = ?
                    """, target.eventId(), target.sectionIndex(), target.seatIndex());
        } else {
            redis.opsForValue().setBit(BookingKeys.sectionSeats(target.eventId(), target.sectionIndex()),
                    target.seatIndex(), true);
        }
    }

    /** 이벤트의 좌석 상태(DB, Redis)와 집계를 처음으로 되돌린다. */
    public void reset(long eventId) {
        jdbcTemplate.update("UPDATE bench_event_seat_status SET reserved = FALSE, reserved_by = NULL WHERE event_id = ?",
                eventId);
        openBookingService.reset(eventId);
        holders.clear();
        counters.values().forEach(Counters::clear);
    }

    public Map<String, Map<String, Long>> report() {
        Map<String, Map<String, Long>> report = new LinkedHashMap<>();
        counters.forEach((strategy, counter) -> report.put(strategy.value(), Map.of(
                "successes", counter.successes.get(),
                "conflicts", counter.conflicts.get(),
                "doubleBookings", counter.doubleBookings.get())));
        return report;
    }

    // ---------- DB ----------

    private boolean dbNaive(SeatTarget target) {
        Boolean reserved = selectReserved(target, false);
        if (reserved) {
            return false;
        }
        jdbcTemplate.update("""
                UPDATE bench_event_seat_status SET reserved = TRUE, reserved_by = ?
                WHERE event_id = ? AND section_index = ? AND seat_index = ?
                """, target.userId(), target.eventId(), target.sectionIndex(), target.seatIndex());
        return true;
    }

    private boolean dbLock(SeatTarget target) {
        return transactionTemplate.execute(status -> {
            Boolean reserved = selectReserved(target, true);
            if (reserved) {
                return false;
            }
            jdbcTemplate.update("""
                    UPDATE bench_event_seat_status SET reserved = TRUE, reserved_by = ?
                    WHERE event_id = ? AND section_index = ? AND seat_index = ?
                    """, target.userId(), target.eventId(), target.sectionIndex(), target.seatIndex());
            return true;
        });
    }

    private boolean dbConditional(SeatTarget target) {
        int updated = jdbcTemplate.update("""
                UPDATE bench_event_seat_status SET reserved = TRUE, reserved_by = ?
                WHERE event_id = ? AND section_index = ? AND seat_index = ? AND reserved = FALSE
                """, target.userId(), target.eventId(), target.sectionIndex(), target.seatIndex());
        if (updated == 0) {
            selectReserved(target, false); // 없는 좌석이면 400
        }
        return updated == 1;
    }

    private Boolean selectReserved(SeatTarget target, boolean forUpdate) {
        List<Boolean> rows = jdbcTemplate.queryForList("""
                SELECT reserved FROM bench_event_seat_status
                WHERE event_id = ? AND section_index = ? AND seat_index = ?
                """ + (forUpdate ? " FOR UPDATE" : ""), Boolean.class,
                target.eventId(), target.sectionIndex(), target.seatIndex());
        if (rows.isEmpty()) {
            throw ApiException.badRequest("좌석이 존재하지 않습니다.");
        }
        return rows.getFirst();
    }

    // ---------- Redis ----------

    private boolean redisNaive(SeatTarget target) {
        String key = BookingKeys.sectionSeats(target.eventId(), target.sectionIndex());
        requireSeatInRange(target);
        Boolean available = redis.opsForValue().getBit(key, target.seatIndex());
        if (!Boolean.TRUE.equals(available)) {
            return false;
        }
        redis.opsForValue().setBit(key, target.seatIndex(), false);
        return true;
    }

    private boolean redisLua(SeatTarget target) {
        Long result = redis.execute(RESERVE_LUA, List.of(
                        BookingKeys.sectionSeats(target.eventId(), target.sectionIndex()),
                        BookingKeys.sectionSeatsLen(target.eventId(), target.sectionIndex())),
                String.valueOf(target.seatIndex()));
        if (result == null || result < 0) {
            throw ApiException.badRequest("좌석이 존재하지 않습니다.");
        }
        return result == 1;
    }

    private void requireSeatInRange(SeatTarget target) {
        String len = redis.opsForValue().get(BookingKeys.sectionSeatsLen(target.eventId(), target.sectionIndex()));
        if (len == null || target.seatIndex() < 0 || target.seatIndex() >= Integer.parseInt(len)) {
            throw ApiException.badRequest("좌석이 존재하지 않습니다.");
        }
    }

    private static String key(SeatLockStrategy strategy, SeatTarget target) {
        return strategy.value() + ":" + target.eventId() + ":" + target.sectionIndex() + ":" + target.seatIndex();
    }

    private static final class Counters {
        final AtomicLong successes = new AtomicLong();
        final AtomicLong conflicts = new AtomicLong();
        final AtomicLong doubleBookings = new AtomicLong();

        void clear() {
            successes.set(0);
            conflicts.set(0);
            doubleBookings.set(0);
        }
    }
}
