package com.realticket.domain.booking.dto;

public record SeatResponse(long eventId, int sectionIndex, int seatIndex, SeatStatus acceptedStatus) {
}
