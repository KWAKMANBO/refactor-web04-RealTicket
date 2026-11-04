package com.realticket.domain.place.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SectionCreateRequest(
        @NotBlank String name,
        @NotNull @Positive Integer colLen,
        @NotEmpty List<@NotNull Boolean> seats,
        @NotNull Long placeId,
        /** 구역 순서. 작은 값부터 sectionIndex 0, 1, 2 ... 가 된다. */
        @NotNull Integer order) {
}
