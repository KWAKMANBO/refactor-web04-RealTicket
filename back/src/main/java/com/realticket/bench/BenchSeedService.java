package com.realticket.bench;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.realticket.domain.booking.service.OpenBookingService;
import com.realticket.domain.event.entity.Event;
import com.realticket.domain.event.repository.EventRepository;
import com.realticket.domain.place.entity.Place;
import com.realticket.domain.place.entity.Section;
import com.realticket.domain.place.repository.PlaceRepository;
import com.realticket.domain.place.repository.SectionRepository;
import com.realticket.domain.program.entity.Program;
import com.realticket.domain.program.repository.ProgramRepository;
import com.realticket.global.exception.ApiException;

import lombok.RequiredArgsConstructor;

/**
 * 측정용 데이터 생성. 같은 공연장을 두 구조로 동시에 저장한다.
 * <ul>
 *     <li>운영 구조: sections.seats (구역별 JSON 배열)</li>
 *     <li>비교 구조: bench_seats (좌석 1개 = 1행)</li>
 *     <li>좌석 상태: Redis 비트맵(예매 오픈) + bench_event_seat_status (DB)</li>
 * </ul>
 */
@Profile("bench")
@Service
@RequiredArgsConstructor
public class BenchSeedService {

    private static final int MAX_TOTAL_SEATS = 200_000;

    private final PlaceRepository placeRepository;
    private final SectionRepository sectionRepository;
    private final ProgramRepository programRepository;
    private final EventRepository eventRepository;
    private final OpenBookingService openBookingService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public record SeedResult(long placeId, long eventId, int sections, int seatsPerSection, int colLen,
            int totalSeats) {
    }

    public SeedResult seed(int sectionCount, int seatsPerSection, int colLen) {
        if (sectionCount < 1 || seatsPerSection < 1 || colLen < 1) {
            throw ApiException.badRequest("sections, seatsPerSection, colLen 은 1 이상이어야 합니다.");
        }
        if ((long) sectionCount * seatsPerSection > MAX_TOTAL_SEATS) {
            throw ApiException.badRequest("전체 좌석 수는 " + MAX_TOTAL_SEATS + " 이하여야 합니다.");
        }

        SeedResult result = transactionTemplate.execute(status -> {
            Place place = placeRepository.save(Place.builder()
                    .name("벤치마크 공연장 " + sectionCount + "x" + seatsPerSection)
                    .address("측정용")
                    .overviewSvg("/overview.svg")
                    .overviewHeight(1000)
                    .overviewWidth(1500)
                    .overviewPoints("[]")
                    .build());

            List<Boolean> seats = Collections.nCopies(seatsPerSection, Boolean.TRUE);
            List<Long> sectionIds = new ArrayList<>();
            for (int i = 0; i < sectionCount; i++) {
                Section section = sectionRepository.save(Section.builder()
                        .name("S" + (i + 1))
                        .colLen(colLen)
                        .seats(new ArrayList<>(seats))
                        .place(place)
                        .build());
                sectionIds.add(section.getId());
            }
            place.replaceSectionIds(sectionIds);

            Program program = programRepository.save(Program.builder()
                    .name("벤치마크 공연")
                    .profileUrl("/profile.png")
                    .runningTime(120)
                    .genre("측정")
                    .actors("-")
                    .price(10000)
                    .place(place)
                    .build());
            LocalDateTime now = LocalDateTime.now();
            Event event = eventRepository.save(Event.builder()
                    .program(program)
                    .place(place)
                    .reservationOpenDate(now.minusMinutes(1))
                    .reservationCloseDate(now.plusDays(1))
                    .runningDate(now.plusDays(2))
                    .build());
            placeRepository.flush();

            insertNormalizedSeats(sectionIds, seatsPerSection, colLen);
            insertSeatStatus(event.getId(), sectionCount, seatsPerSection);

            return new SeedResult(place.getId(), event.getId(), sectionCount, seatsPerSection, colLen,
                    sectionCount * seatsPerSection);
        });

        // Redis 좌석 비트맵 생성 (커밋 후에 실행해야 이벤트를 읽을 수 있다)
        openBookingService.open(result.eventId());
        return result;
    }

    private void insertNormalizedSeats(List<Long> sectionIds, int seatsPerSection, int colLen) {
        List<Object[]> rows = new ArrayList<>(sectionIds.size() * seatsPerSection);
        for (Long sectionId : sectionIds) {
            for (int seat = 0; seat < seatsPerSection; seat++) {
                rows.add(new Object[] {sectionId, seat, seat / colLen + 1, seat % colLen + 1, true});
            }
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO bench_seats (section_id, seat_index, row_no, col_no, is_seat) VALUES (?, ?, ?, ?, ?)",
                rows);
    }

    private void insertSeatStatus(long eventId, int sectionCount, int seatsPerSection) {
        List<Object[]> rows = new ArrayList<>(sectionCount * seatsPerSection);
        for (int section = 0; section < sectionCount; section++) {
            for (int seat = 0; seat < seatsPerSection; seat++) {
                rows.add(new Object[] {eventId, section, seat});
            }
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO bench_event_seat_status (event_id, section_index, seat_index) VALUES (?, ?, ?)", rows);
    }
}
