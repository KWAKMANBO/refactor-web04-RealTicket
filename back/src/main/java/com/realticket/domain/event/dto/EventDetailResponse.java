package com.realticket.domain.event.dto;

import java.time.Instant;

import com.realticket.domain.event.entity.Event;
import com.realticket.domain.program.dto.PlaceResponse;
import com.realticket.domain.program.entity.Program;
import com.realticket.global.config.TimeConfig;

/**
 * 이벤트(회차) 상세. name 은 프로그램 이름이다.
 * (Nest 버전은 name 을 비워 보냈지만 프런트엔드는 이 값을 화면에 표시한다)
 */
public record EventDetailResponse(Long id, String name, PlaceResponse place, int price, int runningTime,
        Instant runningDate, Instant reservationOpenDate, Instant reservationCloseDate) {

    public static EventDetailResponse from(Event event) {
        Program program = event.getProgram();
        return new EventDetailResponse(
                event.getId(),
                program.getName(),
                PlaceResponse.from(event.getPlace()),
                program.getPrice(),
                program.getRunningTime(),
                TimeConfig.toInstant(event.getRunningDate()),
                TimeConfig.toInstant(event.getReservationOpenDate()),
                TimeConfig.toInstant(event.getReservationCloseDate()));
    }
}
