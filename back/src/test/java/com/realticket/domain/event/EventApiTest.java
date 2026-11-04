package com.realticket.domain.event;

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
import com.realticket.domain.event.repository.EventRepository;
import com.realticket.domain.place.entity.Place;
import com.realticket.domain.program.entity.Program;
import com.realticket.support.IntegrationTest;

class EventApiTest extends IntegrationTest {

    @Autowired
    EventRepository eventRepository;

    @Nested
    @DisplayName("상세 조회")
    class Detail {

        @Test
        void 프로그램_이름과_장소_가격_시간을_함께_반환한다() throws Exception {
            Place place = fixtures.place("세종문화회관");
            Program program = fixtures.program("맘마미아", place);
            Event event = fixtures.event(program,
                    LocalDateTime.of(2099, 1, 1, 10, 0),
                    LocalDateTime.of(2099, 1, 5, 18, 0),
                    LocalDateTime.of(2099, 1, 5, 19, 30));

            mockMvc.perform(get("/event/{id}", event.getId()).cookie(loginCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(event.getId()))
                    .andExpect(jsonPath("$.name").value("맘마미아"))
                    .andExpect(jsonPath("$.place.id").value(place.getId()))
                    .andExpect(jsonPath("$.place.name").value("세종문화회관"))
                    .andExpect(jsonPath("$.price").value(15000))
                    .andExpect(jsonPath("$.runningTime").value(7200))
                    .andExpect(jsonPath("$.reservationOpenDate").value("2099-01-01T01:00:00Z"))
                    .andExpect(jsonPath("$.reservationCloseDate").value("2099-01-05T09:00:00Z"))
                    .andExpect(jsonPath("$.runningDate").value("2099-01-05T10:30:00Z"));
        }

        @Test
        void 로그인하지_않았으면_403() throws Exception {
            mockMvc.perform(get("/event/{id}", 1))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 없는_이벤트면_404() throws Exception {
            mockMvc.perform(get("/event/{id}", 999).cookie(loginCookie()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("추가")
    class Create {

        @Test
        void UTC로_받은_날짜를_한국_시간으로_저장한다() throws Exception {
            Program program = fixtures.program("맘마미아", fixtures.place("세종문화회관"));

            mockMvc.perform(post("/event").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request(program.getId(),
                                    "2099-01-01T01:00:00Z", "2099-01-05T09:00:00Z", "2099-01-05T10:30:00Z"))))
                    .andExpect(status().isCreated());

            assertThat(eventRepository.findAll()).singleElement().satisfies(event -> {
                assertThat(event.getReservationOpenDate()).isEqualTo(LocalDateTime.of(2099, 1, 1, 10, 0));
                assertThat(event.getReservationCloseDate()).isEqualTo(LocalDateTime.of(2099, 1, 5, 18, 0));
                assertThat(event.getRunningDate()).isEqualTo(LocalDateTime.of(2099, 1, 5, 19, 30));
            });
        }

        @Test
        void 예약오픈_예약마감_시작_순서가_아니면_400() throws Exception {
            Program program = fixtures.program("맘마미아", fixtures.place("세종문화회관"));

            mockMvc.perform(post("/event").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request(program.getId(),
                                    "2099-01-05T09:00:00Z", "2099-01-01T01:00:00Z", "2099-01-05T10:30:00Z"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("예약오픈일자 < 예약마감일자 <= 시작날짜 형식이어야 합니다."));
        }

        @Test
        void 없는_프로그램이면_404() throws Exception {
            mockMvc.perform(post("/event").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request(999L,
                                    "2099-01-01T01:00:00Z", "2099-01-05T09:00:00Z", "2099-01-05T10:30:00Z"))))
                    .andExpect(status().isNotFound());
        }

        private Map<String, Object> request(Long programId, String open, String close, String running) {
            return Map.of("programId", programId, "reservationOpenDate", open,
                    "reservationCloseDate", close, "runningDate", running);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        void 관리자는_이벤트를_삭제할_수_있다() throws Exception {
            Program program = fixtures.program("맘마미아", fixtures.place("세종문화회관"));
            LocalDateTime now = LocalDateTime.now();
            Event event = fixtures.event(program, now, now.plusDays(1), now.plusDays(2));

            mockMvc.perform(delete("/event/{id}", event.getId()).cookie(adminCookie()))
                    .andExpect(status().isOk());

            assertThat(eventRepository.count()).isZero();
        }

        @Test
        void 없는_이벤트면_404() throws Exception {
            mockMvc.perform(delete("/event/{id}", 999).cookie(adminCookie()))
                    .andExpect(status().isNotFound());
        }
    }
}
