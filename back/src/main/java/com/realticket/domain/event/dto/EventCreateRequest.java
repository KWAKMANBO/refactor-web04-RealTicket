package com.realticket.domain.event.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/** 날짜는 ISO-8601 (예: "2024-11-18T01:00:00Z") */
public record EventCreateRequest(
        @NotNull Instant runningDate,
        @NotNull Instant reservationOpenDate,
        @NotNull Instant reservationCloseDate,
        @NotNull Long programId) {
}
