package com.realticket.domain.reservation.dto;

import java.time.Instant;

/** 예매 내역. seats 예) "A구역 1행 2열, A구역 1행 3열" */
public record ReservationResponse(Long id, String programName, Instant runningDate, String placeName, String seats) {
}
