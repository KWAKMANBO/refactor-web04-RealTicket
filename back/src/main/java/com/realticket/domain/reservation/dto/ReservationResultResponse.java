package com.realticket.domain.reservation.dto;

import java.time.Instant;
import java.util.List;

/** 예매 확정 결과. seats 예) ["A구역 1행 2열", "A구역 1행 3열"] */
public record ReservationResultResponse(String programName, Instant runningDate, String placeName, int price,
        List<String> seats) {
}
