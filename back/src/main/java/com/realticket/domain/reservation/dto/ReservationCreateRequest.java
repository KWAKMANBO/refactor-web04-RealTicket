package com.realticket.domain.reservation.dto;

import java.util.List;

import com.realticket.domain.booking.service.Seat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ReservationCreateRequest(
        @NotNull Long eventId,
        @NotNull @Size(min = 1, max = 4, message = "예매 가능한 좌석 수는 1~4개 입니다.") List<@Valid @NotNull SeatItem> seats) {

    public record SeatItem(@NotNull @PositiveOrZero Integer sectionIndex, @NotNull @PositiveOrZero Integer seatIndex) {

        public Seat toSeat() {
            return new Seat(sectionIndex, seatIndex);
        }
    }
}
