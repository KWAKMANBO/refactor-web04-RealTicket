package com.realticket.domain.booking.service;

import java.util.Set;

/** 좌석 선택 중인 사용자의 인원 수와 선점 좌석 */
public record InBookingSnapshot(int bookingAmount, Set<Seat> bookedSeats) {
}
