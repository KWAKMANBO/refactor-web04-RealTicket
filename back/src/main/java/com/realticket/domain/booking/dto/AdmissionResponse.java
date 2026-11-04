package com.realticket.domain.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 예매 입장 요청 결과. 바로 입장하면 enteringStatus=true, 대기열에 들어가면 waitingStatus=true 와 대기 번호.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdmissionResponse(boolean waitingStatus, boolean enteringStatus, Long userOrder) {

    public static AdmissionResponse entering() {
        return new AdmissionResponse(false, true, null);
    }

    public static AdmissionResponse waiting(long order) {
        return new AdmissionResponse(true, false, order);
    }
}
