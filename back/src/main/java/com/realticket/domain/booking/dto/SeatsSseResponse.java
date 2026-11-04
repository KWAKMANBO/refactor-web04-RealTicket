package com.realticket.domain.booking.dto;

/** 좌석 SSE 데이터. 구역별 좌석 배열, true 면 예약 가능 */
public record SeatsSseResponse(boolean[][] seatStatus) {
}
