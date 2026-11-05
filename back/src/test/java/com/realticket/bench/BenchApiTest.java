package com.realticket.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.realticket.domain.booking.repository.OpenEventRepository;
import com.realticket.support.IntegrationTest;

@ActiveProfiles("bench")
class BenchApiTest extends IntegrationTest {

    @Autowired
    BenchSeedService seedService;

    @Autowired
    BenchLayoutService layoutService;

    @Autowired
    BenchSeatService seatService;

    @Autowired
    OpenEventRepository openEventRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBenchTables() {
        jdbcTemplate.execute("TRUNCATE TABLE bench_seats");
        jdbcTemplate.execute("TRUNCATE TABLE bench_event_seat_status");
    }

    @Test
    void 같은_공연장을_두_구조로_만들고_예매를_연다() {
        BenchSeedService.SeedResult seed = seedService.seed(3, 50, 10);

        assertThat(seed.totalSeats()).isEqualTo(150);
        assertThat(count("bench_seats")).isEqualTo(150);
        assertThat(count("bench_event_seat_status")).isEqualTo(150);
        assertThat(count("sections")).isEqualTo(3);
        assertThat(openEventRepository.isOpened(seed.eventId())).isTrue();
    }

    @Test
    void 두_구조의_좌석_배치_응답은_같다() throws Exception {
        BenchSeedService.SeedResult seed = seedService.seed(4, 37, 8);

        String denormalized = objectMapper.writeValueAsString(layoutService.denormalized(seed.placeId()));
        String normalized = objectMapper.writeValueAsString(layoutService.normalized(seed.placeId()));

        assertThat(normalized).isEqualTo(denormalized);
    }

    @ParameterizedTest
    @EnumSource(value = SeatLockStrategy.class, names = {"DB_LOCK", "DB_CONDITIONAL", "REDIS_LUA"})
    void 원자적인_방식은_같은_좌석을_동시에_잡아도_한_명만_성공한다(SeatLockStrategy strategy) throws Exception {
        BenchSeedService.SeedResult seed = seedService.seed(1, 10, 5);

        List<Boolean> results = reserveConcurrently(strategy, seed.eventId(), 50);

        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(seatService.report().get(strategy.value()).get("doubleBookings")).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = SeatLockStrategy.class, names = {"DB_NAIVE", "REDIS_NAIVE"})
    void 원자적이지_않은_방식도_동작은_하고_중복_예매를_집계한다(SeatLockStrategy strategy) throws Exception {
        BenchSeedService.SeedResult seed = seedService.seed(1, 10, 5);

        List<Boolean> results = reserveConcurrently(strategy, seed.eventId(), 50);

        // 경쟁 결과는 실행마다 다르므로, 성공 수와 중복 예매 집계가 맞물리는지만 확인한다
        long successes = results.stream().filter(Boolean::booleanValue).count();
        assertThat(successes).isGreaterThanOrEqualTo(1);
        assertThat(seatService.report().get(strategy.value()).get("doubleBookings")).isEqualTo(successes - 1);
    }

    @ParameterizedTest
    @EnumSource(SeatLockStrategy.class)
    void 비운_좌석은_다시_잡을_수_있다(SeatLockStrategy strategy) {
        BenchSeedService.SeedResult seed = seedService.seed(1, 10, 5);
        var target = new BenchSeatService.SeatTarget(seed.eventId(), 0, 3, "u1");

        assertThat(seatService.reserve(strategy, target)).isTrue();
        assertThat(seatService.reserve(strategy, target)).isFalse();
        seatService.release(strategy, target);
        assertThat(seatService.reserve(strategy, target)).isTrue();
    }

    private List<Boolean> reserveConcurrently(SeatLockStrategy strategy, long eventId, int users) throws Exception {
        seatService.reset(eventId);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(users)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < users; i++) {
                String userId = "u" + i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return seatService.reserve(strategy,
                            new BenchSeatService.SeatTarget(eventId, 0, 0, userId));
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
