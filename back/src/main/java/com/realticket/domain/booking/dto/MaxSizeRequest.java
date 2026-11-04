package com.realticket.domain.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record MaxSizeRequest(@NotNull @PositiveOrZero Integer maxSize) {
}
