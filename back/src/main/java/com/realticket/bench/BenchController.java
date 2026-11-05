package com.realticket.bench;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.realticket.domain.place.dto.PlaceSeatsResponse;
import com.zaxxer.hikari.HikariDataSource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

/** 성능 측정 전용 API (bench 프로필에서만 열린다, 인증 없음) */
@Tag(name = "Bench")
@Profile("bench")
@RestController
@RequestMapping("/bench")
@RequiredArgsConstructor
public class BenchController {

    private final BenchSeedService seedService;
    private final BenchLayoutService layoutService;
    private final BenchSeatService seatService;
    private final DataSource dataSource;
    private final Environment environment;

    @Operation(summary = "측정 데이터 생성", description = "공연장(두 가지 구조), 공연, 열린 회차를 만든다.")
    @PostMapping("/seed")
    public BenchSeedService.SeedResult seed(
            @RequestParam(defaultValue = "10") int sections,
            @RequestParam(defaultValue = "300") int seatsPerSection,
            @RequestParam(defaultValue = "20") int colLen) {
        return seedService.seed(sections, seatsPerSection, colLen);
    }

    @Operation(summary = "[비교 1] 좌석 배치 조회", description = "type: denormalized(운영) / normalized(좌석 1개=1행)")
    @GetMapping("/layout/{placeId}")
    public PlaceSeatsResponse layout(@PathVariable long placeId, @RequestParam String type) {
        return switch (type) {
            case "denormalized" -> layoutService.denormalized(placeId);
            case "normalized" -> layoutService.normalized(placeId);
            default -> throw com.realticket.global.exception.ApiException.badRequest(
                    "type 은 denormalized 또는 normalized 입니다.");
        };
    }

    @Operation(summary = "[비교 2] 좌석 선점", description = "성공 200, 이미 예약된 좌석 409")
    @PostMapping("/seat/{strategy}/reserve")
    public ResponseEntity<Map<String, Object>> reserve(@PathVariable String strategy,
            @Valid @RequestBody SeatRequest request) {
        boolean reserved = seatService.reserve(SeatLockStrategy.from(strategy), request.toTarget());
        return ResponseEntity.status(reserved ? HttpStatus.OK : HttpStatus.CONFLICT)
                .body(Map.of("reserved", reserved));
    }

    @Operation(summary = "[비교 2] 좌석 비우기", description = "처리량 측정에서 좌석을 순환시킬 때 사용")
    @PostMapping("/seat/{strategy}/release")
    public void release(@PathVariable String strategy, @Valid @RequestBody SeatRequest request) {
        seatService.release(SeatLockStrategy.from(strategy), request.toTarget());
    }

    @Operation(summary = "좌석 상태와 집계 초기화")
    @PostMapping("/seat/reset")
    public void reset(@RequestParam long eventId) {
        seatService.reset(eventId);
    }

    @Operation(summary = "방식별 성공/실패/중복 예매 집계")
    @GetMapping("/seat/report")
    public Map<String, Map<String, Long>> report() {
        return seatService.report();
    }

    @Operation(summary = "측정 환경 정보", description = "결과와 함께 기록할 서버 환경")
    @GetMapping("/info")
    public Map<String, Object> info() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("availableProcessors", runtime.availableProcessors());
        info.put("maxHeapMb", runtime.maxMemory() / 1024 / 1024);
        info.put("virtualThreads", environment.getProperty("spring.threads.virtual.enabled"));
        if (dataSource instanceof HikariDataSource hikari) {
            info.put("dbPoolSize", hikari.getMaximumPoolSize());
        }
        return info;
    }

    public record SeatRequest(
            @NotNull Long eventId,
            @NotNull @PositiveOrZero Integer sectionIndex,
            @NotNull @PositiveOrZero Integer seatIndex,
            @NotNull String userId) {

        BenchSeatService.SeatTarget toTarget() {
            return new BenchSeatService.SeatTarget(eventId, sectionIndex, seatIndex, userId);
        }
    }
}
