package com.realticket.support;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

import com.realticket.domain.event.entity.Event;
import com.realticket.domain.event.repository.EventRepository;
import com.realticket.domain.place.entity.Place;
import com.realticket.domain.place.entity.Section;
import com.realticket.domain.place.repository.PlaceRepository;
import com.realticket.domain.place.repository.SectionRepository;
import com.realticket.domain.program.entity.Program;
import com.realticket.domain.program.repository.ProgramRepository;

import lombok.RequiredArgsConstructor;

/** 테스트 데이터 생성 도우미 */
@TestComponent
@RequiredArgsConstructor
public class TestFixtures {

    private final PlaceRepository placeRepository;
    private final SectionRepository sectionRepository;
    private final ProgramRepository programRepository;
    private final EventRepository eventRepository;

    /** 구역 이름마다 2x2 좌석(마지막 칸은 빈 칸)을 가진 장소 */
    @Transactional
    public Place place(String name, String... sectionNames) {
        Place place = placeRepository.save(Place.builder()
                .name(name)
                .address("서울특별시")
                .overviewSvg("/overview.svg")
                .overviewHeight(1000)
                .overviewWidth(1500)
                .overviewPoints("[]")
                .build());
        List<Long> sectionIds = new ArrayList<>();
        for (String sectionName : sectionNames) {
            Section section = sectionRepository.save(Section.builder()
                    .name(sectionName)
                    .colLen(2)
                    .seats(List.of(true, true, true, false))
                    .place(place)
                    .build());
            sectionIds.add(section.getId());
        }
        place.replaceSectionIds(sectionIds);
        return placeRepository.save(place);
    }

    public Program program(String name, Place place) {
        return programRepository.save(Program.builder()
                .name(name)
                .profileUrl("/profile.png")
                .runningTime(7200)
                .genre("뮤지컬")
                .actors("배우1, 배우2")
                .price(15000)
                .place(place)
                .build());
    }

    public Event event(Program program, LocalDateTime openDate, LocalDateTime closeDate, LocalDateTime runningDate) {
        return eventRepository.save(Event.builder()
                .program(program)
                .place(program.getPlace())
                .reservationOpenDate(openDate)
                .reservationCloseDate(closeDate)
                .runningDate(runningDate)
                .build());
    }
}
