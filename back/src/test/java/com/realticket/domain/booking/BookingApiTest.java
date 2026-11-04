package com.realticket.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.realticket.domain.booking.repository.BookingKeys;
import com.realticket.domain.booking.repository.OpenEventRepository;
import com.realticket.domain.booking.repository.SeatRepository;
import com.realticket.domain.booking.service.BookingService;
import com.realticket.domain.booking.service.OpenBookingService;
import com.realticket.domain.booking.service.Seat;
import com.realticket.domain.event.entity.Event;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.auth.UserStatus;
import com.realticket.global.exception.ApiException;
import com.realticket.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

class BookingApiTest extends IntegrationTest {

    @Autowired
    BookingService bookingService;

    @Autowired
    OpenBookingService openBookingService;

    @Autowired
    OpenEventRepository openEventRepository;

    @Autowired
    SeatRepository seatRepository;

    @Autowired
    StringRedisTemplate redis;

    @Nested
    @DisplayName("입장 요청")
    class Admission {

        @Test
        void 오픈되지_않은_이벤트면_400() throws Exception {
            SessionUser user = booking.loginUser();

            mockMvc.perform(get("/booking/permission/{id}", 999).cookie(cookie(user)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("예약이 오픈되지 않았습니다."));
        }

        @Test
        void 자리가_있으면_바로_입장한다() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.loginUser();

            mockMvc.perform(get("/booking/permission/{id}", event.getId()).cookie(cookie(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enteringStatus").value(true))
                    .andExpect(jsonPath("$.waitingStatus").value(false))
                    .andExpect(jsonPath("$.userOrder").doesNotExist());

            SessionUser refreshed = booking.refresh(user);
            assertThat(refreshed.status()).isEqualTo(UserStatus.ENTERING);
            assertThat(refreshed.targetEventId()).isEqualTo(event.getId());
        }

        @Test
        void 자리가_없으면_대기열에_순서대로_들어간다() throws Exception {
            Event event = booking.openedEvent(1);
            booking.requestAdmission(event.getId());
            SessionUser second = booking.loginUser();
            SessionUser third = booking.loginUser();

            mockMvc.perform(get("/booking/permission/{id}", event.getId()).cookie(cookie(second)))
                    .andExpect(jsonPath("$.waitingStatus").value(true))
                    .andExpect(jsonPath("$.enteringStatus").value(false))
                    .andExpect(jsonPath("$.userOrder").value(1));
            mockMvc.perform(get("/booking/permission/{id}", event.getId()).cookie(cookie(third)))
                    .andExpect(jsonPath("$.userOrder").value(2));

            assertThat(booking.refresh(second).status()).isEqualTo(UserStatus.WAITING);
        }

        @Test
        void 대기_중에_다시_요청해도_순서가_바뀌지_않는다() throws Exception {
            Event event = booking.openedEvent(1);
            booking.requestAdmission(event.getId());
            SessionUser waiting = booking.loginUser();

            mockMvc.perform(get("/booking/permission/{id}", event.getId()).cookie(cookie(waiting)))
                    .andExpect(jsonPath("$.userOrder").value(1));
            mockMvc.perform(get("/booking/permission/{id}", event.getId()).cookie(cookie(waiting)))
                    .andExpect(jsonPath("$.userOrder").value(1));
        }

        @Test
        void 대기열_현황_SSE는_맨_앞_번호와_대기_인원을_보낸다() throws Exception {
            Event event = booking.openedEvent(1);
            booking.requestAdmission(event.getId());
            SessionUser waiting = booking.requestAdmission(event.getId());
            booking.requestAdmission(event.getId());

            MvcResult result = mockMvc.perform(get("/booking/re-permission/{id}", event.getId())
                            .cookie(cookie(waiting)).accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString())
                    .contains("\"headOrder\":1", "\"totalWaiting\":2", "\"throughputRate\":1000");
        }
    }

    @Nested
    @DisplayName("좌석 선택 화면")
    class SeatSelection {

        @Test
        void 좌석_SSE에_연결하면_좌석_선택_중이_되고_현재_좌석을_받는다() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.requestAdmission(event.getId());

            MvcResult result = mockMvc.perform(get("/booking/seat/{id}", event.getId())
                            .cookie(cookie(user)).accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString())
                    .contains("{\"seatStatus\":[[true,true,true,false],[true,true,true,false]]}")
                    .contains("retry:5000");
            assertThat(booking.refresh(user).status()).isEqualTo(UserStatus.SELECTING_SEAT);
        }

        @Test
        void 입장_허가가_없으면_좌석_SSE는_401() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.loginUser();

            mockMvc.perform(get("/booking/seat/{id}", event.getId()).cookie(cookie(user)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 좌석을_선점하고_취소한다() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.selectingSeat(event.getId(), 1);

            mockMvc.perform(post("/booking").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(seatRequest(event.getId(), 0, 1, "reserved")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value(event.getId()))
                    .andExpect(jsonPath("$.sectionIndex").value(0))
                    .andExpect(jsonPath("$.seatIndex").value(1))
                    .andExpect(jsonPath("$.acceptedStatus").value("reserved"));
            assertThat(seatRepository.findAll(event.getId())[0][1]).isFalse();

            mockMvc.perform(post("/booking").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(seatRequest(event.getId(), 0, 1, "deleted")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.acceptedStatus").value("deleted"));
            assertThat(seatRepository.findAll(event.getId())[0][1]).isTrue();
        }

        @Test
        void 다른_사람이_선점한_좌석은_409() throws Exception {
            Event event = booking.openedEvent(10);
            booking.selectingSeatWith(event.getId(), new Seat(0, 0));
            SessionUser other = booking.selectingSeat(event.getId(), 1);

            mockMvc.perform(post("/booking").cookie(cookie(other)).contentType(MediaType.APPLICATION_JSON)
                            .content(seatRequest(event.getId(), 0, 0, "reserved")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("이미 예약된 좌석입니다."));
        }

        @Test
        void 설정한_인원보다_많이_선점하면_400() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.selectingSeatWith(event.getId(), new Seat(0, 0));

            mockMvc.perform(post("/booking").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(seatRequest(event.getId(), 0, 1, "reserved")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("예약 가능한 좌석 수를 초과했습니다."));
        }

        @Test
        void 없는_좌석이면_400() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.selectingSeat(event.getId(), 1);

            mockMvc.perform(post("/booking").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(seatRequest(event.getId(), 0, 10, "reserved")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("좌석이 존재하지 않습니다."));
            mockMvc.perform(post("/booking").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(seatRequest(event.getId(), 5, 0, "reserved")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 선점하지_않은_좌석은_취소할_수_없다() throws Exception {
            Event event = booking.openedEvent(10);
            booking.selectingSeatWith(event.getId(), new Seat(0, 0));
            SessionUser other = booking.selectingSeat(event.getId(), 1);

            mockMvc.perform(post("/booking").cookie(cookie(other)).contentType(MediaType.APPLICATION_JSON)
                            .content(seatRequest(event.getId(), 0, 0, "deleted")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("예약하지 않은 좌석입니다."));
            assertThat(seatRepository.findAll(event.getId())[0][0]).isFalse();
        }

        @Test
        void 좌석_선택_중에_인원을_바꾸면_선점한_좌석이_풀린다() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.selectingSeatWith(event.getId(), new Seat(0, 0), new Seat(1, 1));

            mockMvc.perform(post("/booking/count").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("bookingAmount", 3))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingAmount").value(3));

            boolean[][] seats = seatRepository.findAll(event.getId());
            assertThat(seats[0][0]).isTrue();
            assertThat(seats[1][1]).isTrue();
        }

        @Test
        void 인원은_1명에서_4명까지() throws Exception {
            Event event = booking.openedEvent(10);
            SessionUser user = booking.requestAdmission(event.getId());

            mockMvc.perform(post("/booking/count").cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("bookingAmount", 5))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("떠남과 대기자 입장")
    class LeaveAndAdmit {

        @Test
        void 좌석_화면을_떠나면_좌석이_풀리고_대기자가_입장한다() {
            Event event = booking.openedEvent(1);
            SessionUser leaving = booking.selectingSeatWith(event.getId(), new Seat(0, 0));
            SessionUser waiting = booking.requestAdmission(event.getId());
            assertThat(waiting.status()).isEqualTo(UserStatus.WAITING);

            bookingService.leaveSeatSelection(event.getId(), leaving.sid());

            assertThat(seatRepository.findAll(event.getId())[0][0]).isTrue();
            SessionUser left = booking.refresh(leaving);
            assertThat(left.status()).isEqualTo(UserStatus.LOGIN);
            assertThat(left.targetEventId()).isNull();
            assertThat(booking.refresh(waiting).status()).isEqualTo(UserStatus.ENTERING);
        }

        @Test
        void 입장_허가_후_시간_안에_오지_않으면_만료되고_대기자가_입장한다() {
            Event event = booking.openedEvent(1);
            SessionUser late = booking.requestAdmission(event.getId());
            SessionUser waiting = booking.requestAdmission(event.getId());
            // 입장 허가 시각을 아주 옛날로 바꿔 만료 대상으로 만든다
            redis.opsForZSet().add(BookingKeys.entering(event.getId()), late.sid(), 0);

            bookingService.collectExpiredEntering();

            assertThat(booking.refresh(late).status()).isEqualTo(UserStatus.LOGIN);
            assertThat(booking.refresh(waiting).status()).isEqualTo(UserStatus.ENTERING);
        }

        @Test
        void 관리자가_최대_인원을_늘리면_대기자가_바로_입장한다() throws Exception {
            Event event = booking.openedEvent(1);
            booking.requestAdmission(event.getId());
            SessionUser waiting = booking.requestAdmission(event.getId());

            mockMvc.perform(post("/booking/in-booking-pool-size/event/{id}", event.getId())
                            .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("maxSize", 2))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.maxSize").value(2));

            assertThat(booking.refresh(waiting).status()).isEqualTo(UserStatus.ENTERING);
        }
    }

    @Nested
    @DisplayName("오픈과 마감")
    class OpenAndClose {

        @Test
        void 오픈하면_구역별_좌석_상태가_만들어진다() {
            Event event = booking.openedEvent(10);

            assertThat(openEventRepository.isOpened(event.getId())).isTrue();
            assertThat(seatRepository.findAll(event.getId()))
                    .isEqualTo(new boolean[][] {{true, true, true, false}, {true, true, true, false}});
        }

        @Test
        void 오픈_시각_전이면_열지_않는다() {
            LocalDateTime now = LocalDateTime.now();
            Event event = fixtures.event(fixtures.program("맘마미아", fixtures.place("대극장", "A")),
                    now.plusHours(1), now.plusDays(1), now.plusDays(2));

            openBookingService.open(event.getId());

            assertThat(openEventRepository.isOpened(event.getId())).isFalse();
        }

        @Test
        void 관리자가_오픈_대상을_다시_확인하면_오픈_시각이_지난_이벤트를_연다() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            Event event = fixtures.event(fixtures.program("맘마미아", fixtures.place("대극장", "A")),
                    now.minusMinutes(1), now.plusDays(1), now.plusDays(2));

            mockMvc.perform(post("/booking/reload-open-target").cookie(adminCookie()))
                    .andExpect(status().isOk());

            assertThat(openEventRepository.isOpened(event.getId())).isTrue();
        }

        @Test
        void 마감하면_진행_중인_사용자를_모두_되돌리고_예매_데이터를_지운다() {
            Event event = booking.openedEvent(1);
            SessionUser selecting = booking.selectingSeatWith(event.getId(), new Seat(0, 0));
            SessionUser waiting = booking.requestAdmission(event.getId());

            openBookingService.forceClose(event.getId());

            assertThat(openEventRepository.isOpened(event.getId())).isFalse();
            assertThat(seatRepository.findAll(event.getId())).isNull();
            assertThat(booking.refresh(selecting).status()).isEqualTo(UserStatus.LOGIN);
            assertThat(booking.refresh(waiting).status()).isEqualTo(UserStatus.LOGIN);
            assertThat(redis.keys("*:" + event.getId() + "*")).isEmpty();
        }

        @Test
        void 관리자가_초기화하면_좌석이_처음_상태로_돌아간다() throws Exception {
            Event event = booking.openedEvent(10);
            booking.selectingSeatWith(event.getId(), new Seat(0, 0));

            mockMvc.perform(post("/booking/init/{id}", event.getId()).cookie(adminCookie()))
                    .andExpect(status().isOk());

            assertThat(openEventRepository.isOpened(event.getId())).isTrue();
            assertThat(seatRepository.findAll(event.getId())[0][0]).isTrue();
        }

        @Test
        void 마감된_이벤트에는_입장할_수_없다() {
            Event event = booking.openedEvent(10);
            openBookingService.forceClose(event.getId());
            SessionUser user = booking.loginUser();

            assertThatThrownBy(() -> bookingService.requestAdmission(user, event.getId()))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void 서버_시간을_조회한다() throws Exception {
        long before = System.currentTimeMillis();

        mockMvc.perform(get("/booking/server-time").cookie(loginCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.now").value(org.hamcrest.Matchers.greaterThanOrEqualTo(before)));
    }

    private Cookie cookie(SessionUser user) {
        return new Cookie("SID", user.sid());
    }

    private String seatRequest(long eventId, int sectionIndex, int seatIndex, String expectedStatus)
            throws Exception {
        return json(Map.of("eventId", eventId, "sectionIndex", sectionIndex, "seatIndex", seatIndex,
                "expectedStatus", expectedStatus));
    }
}
