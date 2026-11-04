package com.realticket.domain.program.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProgramCreateRequest(
        @NotBlank String name,
        @NotBlank String profileUrl,
        @NotNull @PositiveOrZero Integer runningTime,
        @NotBlank String genre,
        @NotBlank String actors,
        @NotNull @PositiveOrZero Integer price,
        @NotNull Long placeId) {
}
