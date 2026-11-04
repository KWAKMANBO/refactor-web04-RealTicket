package com.realticket.domain.booking.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 좌석 요청 종류. JSON 값은 "reserved"(선점) / "deleted"(취소) */
public enum SeatStatus {
    RESERVE("reserved"),
    DELETE("deleted");

    private final String value;

    SeatStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static SeatStatus from(String value) {
        for (SeatStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("expectedStatus 는 reserved 또는 deleted 여야 합니다.");
    }
}
