package com.realticket.domain.booking.dto;

import com.realticket.domain.booking.service.Seat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SeatRequest(
        @NotNull Long eventId,
        @NotNull @PositiveOrZero Integer sectionIndex,
        @NotNull @PositiveOrZero Integer seatIndex,
        @NotNull SeatStatus expectedStatus) {

    public Seat seat() {
        return new Seat(sectionIndex, seatIndex);
    }
}
