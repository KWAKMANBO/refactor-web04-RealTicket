package com.realticket.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.realticket.domain.booking.repository.SeatRepository;
import com.realticket.domain.booking.service.BookingService;
import com.realticket.domain.booking.service.Seat;
import com.realticket.domain.event.entity.Event;
import com.realticket.global.auth.SessionUser;
import com.realticket.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

class ReservationApiTest extends IntegrationTest {

    @Autowired
    BookingService bookingService;

    @Autowired
    SeatRepository seatRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("예매 확정")
    class Create {

        @Test
        void 선점한_좌석으로_예매를_저장한다() throws Exception {
            Event event = booking.openedEvent(10);
            // 구역 A 는 2열: seatIndex 1 = 1행 2열, seatIndex 2 = 2행 1열
            SessionUser user = booking.selectingSeatWith(event.getId(), new Seat(0, 2), new Seat(0, 1));

            mockMvc.perform(post("/reservation").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(request(event.getId(), new Seat(0, 1), new Seat(0, 2))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.programName").value("맘마미아"))
                    .andExpect(jsonPath("$.placeName").value("대극장"))
                    .andExpect(jsonPath("$.price").value(15000))
                    .andExpect(jsonPath("$.runningDate").isString())
                    .andExpect(jsonPath("$.seats[0]").value("A구역 1행 2열"))
                    .andExpect(jsonPath("$.seats[1]").value("A구역 2행 1열"));

            assertThat(count("reservations")).isEqualTo(1);
            assertThat(count("reserved_seats")).isEqualTo(2);
        }

        @Test
        void 같은_예매를_두_번_보내면_두_번째는_400() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.selectingSeatWith(event.getId(), new Seat(0, 0));
            String body = request(event.getId(), new Seat(0, 0));

            mockMvc.perform(post("/reservation").cookie(cookie(user))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
            mockMvc.perform(post("/reservation").cookie(cookie(user))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("이미 예매가 완료되었습니다."));

            assertThat(count("reservations")).isEqualTo(1);
        }

        @Test
        void 선점하지_않은_좌석이_있으면_400() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.selectingSeatWith(event.getId(), new Seat(0, 0));

            mockMvc.perform(post("/reservation").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(request(event.getId(), new Seat(0, 1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("선점하지 않은 좌석이 포함되어 있습니다."));
        }

        @Test
        void 설정한_인원만큼_선점하지_않았으면_400() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.selectingSeat(event.getId(), 2);
            bookingService.updateSeat(user, event.getId(), new Seat(0, 0),
                    com.realticket.domain.booking.dto.SeatStatus.RESERVE);

            mockMvc.perform(post("/reservation").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(request(event.getId(), new Seat(0, 0))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("예매 정보 또는 설정한 좌석수가 올바르지 않습니다."));
        }

        @Test
        void 좌석_선택_중이_아니면_401() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.requestAdmission(event.getId());

            mockMvc.perform(post("/reservation").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(request(event.getId(), new Seat(0, 0))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 예매_후_좌석_화면을_떠나도_좌석은_예약된_상태로_남는다() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.selectingSeatWith(event.getId(), new Seat(0, 0));
            mockMvc.perform(post("/reservation").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                    .content(request(event.getId(), new Seat(0, 0)))).andExpect(status().isCreated());

            bookingService.leaveSeatSelection(event.getId(), user.sid());

            assertThat(seatRepository.findAll(event.getId())[0][0]).isFalse();
        }

        @Test
        void 예매_후에는_좌석을_바꿀_수_없다() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.selectingSeatWith(event.getId(), new Seat(0, 0));
            mockMvc.perform(post("/reservation").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                    .content(request(event.getId(), new Seat(0, 0)))).andExpect(status().isCreated());

            mockMvc.perform(post("/booking").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("eventId", event.getId(), "sectionIndex", 0, "seatIndex", 0,
                                    "expectedStatus", "deleted"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("이미 예매가 완료되었습니다."));
        }
    }

    @Nested
    @DisplayName("예매 내역과 취소")
    class ListAndCancel {

        @Test
        void 내_예매_내역을_조회한다() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = reserved(event, new Seat(0, 0), new Seat(1, 1));

            mockMvc.perform(get("/reservation").cookie(cookie(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").isNumber())
                    .andExpect(jsonPath("$[0].programName").value("맘마미아"))
                    .andExpect(jsonPath("$[0].placeName").value("대극장"))
                    .andExpect(jsonPath("$[0].seats").value("A구역 1행 1열, B구역 1행 2열"));
        }

        @Test
        void 예매를_취소하면_좌석이_다시_예약_가능해진다() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = reserved(event, new Seat(0, 0));
            bookingService.leaveSeatSelection(event.getId(), user.sid());
            assertThat(seatRepository.findAll(event.getId())[0][0]).isFalse();
            Long reservationId = jdbcTemplate.queryForObject("SELECT id FROM reservations", Long.class);

            mockMvc.perform(delete("/reservation/{id}", reservationId).cookie(cookie(user)))
                    .andExpect(status().isOk());

            assertThat(seatRepository.findAll(event.getId())[0][0]).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM reservations WHERE deleted_at IS NOT NULL", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM reserved_seats WHERE deleted_at IS NOT NULL", Integer.class)).isEqualTo(1);
            mockMvc.perform(get("/reservation").cookie(cookie(user)))
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void 다른_사람의_예매는_취소할_수_없다() throws Exception {
            Event event = booking.openedEvent(10);
            reserved(event, new Seat(0, 0));
            Long reservationId = jdbcTemplate.queryForObject("SELECT id FROM reservations", Long.class);
            SessionUser other = booking.loginUser();

            mockMvc.perform(delete("/reservation/{id}", reservationId).cookie(cookie(other)))
                    .andExpect(status().isBadRequest());

            assertThat(seatRepository.findAll(event.getId())[0][0]).isFalse();
        }

        private SessionUser reserved(Event event, Seat... seats) throws Exception {
            SessionUser user = booking.selectingSeatWith(event.getId(), seats);
            mockMvc.perform(post("/reservation").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                    .content(request(event.getId(), seats))).andExpect(status().isCreated());
            return user;
        }
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Cookie cookie(SessionUser user) {
        return new Cookie("SID", user.sid());
    }

    private String request(long eventId, Seat... seats) throws Exception {
        List<Map<String, Integer>> items = java.util.Arrays.stream(seats)
                .map(seat -> Map.of("sectionIndex", seat.sectionIndex(), "seatIndex", seat.seatIndex()))
                .toList();
        return json(Map.of("eventId", eventId, "seats", items));
    }
}
