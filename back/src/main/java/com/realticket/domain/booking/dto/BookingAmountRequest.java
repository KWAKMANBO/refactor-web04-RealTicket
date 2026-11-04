package com.realticket.domain.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BookingAmountRequest(@NotNull @Min(1) @Max(4) Integer bookingAmount) {
}
