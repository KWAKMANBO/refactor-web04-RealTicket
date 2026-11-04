package com.realticket.domain.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PlaceCreateRequest(
        @NotBlank String name,
        @NotBlank String address,
        @NotBlank String overviewSvg,
        @NotNull @Positive Integer overviewHeight,
        @NotNull @Positive Integer overviewWidth,
        /** 구역별 좌표 JSON 문자열. 예) [{"id":"1","points":[[0,0],[10,0],[10,10]]}] */
        @NotBlank String overviewPoints) {
}
