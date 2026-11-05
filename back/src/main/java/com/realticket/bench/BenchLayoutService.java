package com.realticket.bench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realticket.domain.place.dto.PlaceSeatsResponse;
import com.realticket.domain.place.entity.Place;
import com.realticket.domain.place.repository.PlaceRepository;
import com.realticket.domain.place.service.PlaceService;
import com.realticket.global.exception.ApiException;

import lombok.RequiredArgsConstructor;

/**
 * [비교 1] 좌석 배치 조회. 두 방식은 같은 응답(JSON)을 만든다.
 * <ul>
 *     <li>denormalized: 운영 코드 그대로 (구역별 JSON 배열, 구역 수만큼의 행)</li>
 *     <li>normalized: 좌석 1개 = 1행 테이블을 JOIN 해서 조립 (좌석 수만큼의 행)</li>
 * </ul>
 */
@Profile("bench")
@Service
@RequiredArgsConstructor
public class BenchLayoutService {

    private final PlaceService placeService;
    private final PlaceRepository placeRepository;
    private final JdbcTemplate jdbcTemplate;

    public PlaceSeatsResponse denormalized(long placeId) {
        return placeService.getSeats(placeId);
    }

    @Transactional(readOnly = true)
    public PlaceSeatsResponse normalized(long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> ApiException.badRequest("해당 장소가 존재하지 않습니다."));

        // 구역 정보 + 좌석 행을 한 번의 JOIN 으로 읽는다 (인덱스: uk_bench_seats_section_seat)
        Map<Long, SectionRows> bySection = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT s.id, s.name, s.col_len, bs.is_seat
                FROM sections s
                JOIN bench_seats bs ON bs.section_id = s.id
                WHERE s.place_id = ?
                ORDER BY s.id, bs.seat_index
                """, rs -> {
            long sectionId = rs.getLong(1);
            bySection.computeIfAbsent(sectionId, id -> {
                try {
                    return new SectionRows(id, rs.getString(2), rs.getInt(3), new ArrayList<>());
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            }).seats().add(rs.getBoolean(4));
        }, placeId);

        List<PlaceSeatsResponse.SectionResponse> sections = place.getSectionIds().stream()
                .map(bySection::get)
                .map(rows -> new PlaceSeatsResponse.SectionResponse(rows.id(), rows.name(), rows.colLen(),
                        rows.seats()))
                .toList();
        return new PlaceSeatsResponse(place.getId(), new PlaceSeatsResponse.Layout(
                place.getOverviewSvg(), sections, place.getOverviewWidth(), place.getOverviewHeight(),
                place.getOverviewPoints()));
    }

    private record SectionRows(long id, String name, int colLen, List<Boolean> seats) {
    }
}
