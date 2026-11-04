package com.realticket.domain.program;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.realticket.domain.event.entity.Event;
import com.realticket.domain.place.entity.Place;
import com.realticket.domain.program.entity.Program;
import com.realticket.domain.program.repository.ProgramRepository;
import com.realticket.support.IntegrationTest;

class ProgramApiTest extends IntegrationTest {

    @Autowired
    ProgramRepository programRepository;

    @Test
    void 프로그램_목록을_장소와_함께_조회한다() throws Exception {
        Place place = fixtures.place("세종문화회관");
        Program program = fixtures.program("맘마미아", place);

        mockMvc.perform(get("/program"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(program.getId()))
                .andExpect(jsonPath("$[0].name").value("맘마미아"))
                .andExpect(jsonPath("$[0].genre").value("뮤지컬"))
                .andExpect(jsonPath("$[0].profileUrl").value("/profile.png"))
                .andExpect(jsonPath("$[0].actors").value("배우1, 배우2"))
                .andExpect(jsonPath("$[0].place.id").value(place.getId()))
                .andExpect(jsonPath("$[0].place.name").value("세종문화회관"));
    }

    @Nested
    @DisplayName("상세 조회")
    class Detail {

        @Test
        void 예약_마감_전인_회차만_시작순으로_포함한다() throws Exception {
            Program program = fixtures.program("맘마미아", fixtures.place("세종문화회관"));
            LocalDateTime now = LocalDateTime.now();
            Event later = fixtures.event(program, now.minusDays(1), now.plusDays(2), LocalDateTime.of(2099, 1, 2, 19, 0));
            Event sooner = fixtures.event(program, now.minusDays(1), now.plusDays(1), LocalDateTime.of(2099, 1, 1, 19, 0));
            fixtures.event(program, now.minusDays(3), now.minusDays(1), now.minusHours(1));

            mockMvc.perform(get("/program/{id}", program.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("맘마미아"))
                    .andExpect(jsonPath("$.runningTime").value(7200))
                    .andExpect(jsonPath("$.price").value(15000))
                    .andExpect(jsonPath("$.place.name").value("세종문화회관"))
                    .andExpect(jsonPath("$.events.length()").value(2))
                    .andExpect(jsonPath("$.events[0].id").value(sooner.getId()))
                    // 한국 시간 19:00 = UTC 10:00
                    .andExpect(jsonPath("$.events[0].runningDate").value("2099-01-01T10:00:00Z"))
                    .andExpect(jsonPath("$.events[1].id").value(later.getId()));
        }

        @Test
        void 없는_프로그램이면_404() throws Exception {
            mockMvc.perform(get("/program/{id}", 999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("해당 프로그램[999]는 존재하지 않습니다."));
        }

        @Test
        void id가_숫자가_아니면_400() throws Exception {
            mockMvc.perform(get("/program/abc"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("추가")
    class Create {

        @Test
        void 관리자는_프로그램을_추가할_수_있다() throws Exception {
            Place place = fixtures.place("세종문화회관");

            mockMvc.perform(post("/program").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(json(request(place.getId()))))
                    .andExpect(status().isCreated());

            assertThat(programRepository.findAll()).singleElement()
                    .satisfies(program -> assertThat(program.getName()).isEqualTo("맘마미아"));
        }

        @Test
        void 없는_장소면_404() throws Exception {
            mockMvc.perform(post("/program").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(json(request(999L))))
                    .andExpect(status().isNotFound());
        }

        private Map<String, Object> request(Long placeId) {
            return Map.of("name", "맘마미아", "profileUrl", "/profile.png", "runningTime", 10000,
                    "genre", "뮤지컬", "actors", "배우", "price", 15000, "placeId", placeId);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        void 회차가_없으면_삭제한다() throws Exception {
            Program program = fixtures.program("맘마미아", fixtures.place("세종문화회관"));

            mockMvc.perform(delete("/program/{id}", program.getId()).cookie(adminCookie()))
                    .andExpect(status().isOk());

            assertThat(programRepository.count()).isZero();
        }

        @Test
        void 회차가_있으면_409() throws Exception {
            Program program = fixtures.program("맘마미아", fixtures.place("세종문화회관"));
            LocalDateTime now = LocalDateTime.now();
            fixtures.event(program, now, now.plusDays(1), now.plusDays(2));

            mockMvc.perform(delete("/program/{id}", program.getId()).cookie(adminCookie()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("해당 프로그램에 대한 이벤트가 존재합니다."));
        }

        @Test
        void 없는_프로그램이면_404() throws Exception {
            mockMvc.perform(delete("/program/{id}", 999).cookie(adminCookie()))
                    .andExpect(status().isNotFound());
        }
    }
}
