package com.realticket.domain.program.dto;

import java.time.Instant;
import java.util.List;

import com.realticket.domain.event.entity.Event;
import com.realticket.domain.program.entity.Program;
import com.realticket.global.config.TimeConfig;

/** 프로그램 상세. events 는 예약 마감 전인 회차만 포함한다. */
public record ProgramDetailResponse(Long id, String name, int runningTime, String genre, String actors,
        PlaceResponse place, String profileUrl, int price, List<EventItem> events) {

    public static ProgramDetailResponse of(Program program, List<Event> openEvents) {
        return new ProgramDetailResponse(
                program.getId(),
                program.getName(),
                program.getRunningTime(),
                program.getGenre(),
                program.getActors(),
                PlaceResponse.from(program.getPlace()),
                program.getProfileUrl(),
                program.getPrice(),
                openEvents.stream().map(EventItem::from).toList());
    }

    public record EventItem(Long id, Instant runningDate) {

        static EventItem from(Event event) {
            return new EventItem(event.getId(), TimeConfig.toInstant(event.getRunningDate()));
        }
    }
}
