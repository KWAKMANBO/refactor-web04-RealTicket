package com.realticket.support;

import java.time.LocalDateTime;

import org.springframework.boot.test.context.TestComponent;

import com.realticket.domain.booking.repository.OpenEventRepository;
import com.realticket.domain.booking.service.BookingService;
import com.realticket.domain.booking.service.OpenBookingService;
import com.realticket.domain.booking.service.Seat;
import com.realticket.domain.booking.dto.SeatStatus;
import com.realticket.domain.event.entity.Event;
import com.realticket.domain.user.entity.User;
import com.realticket.global.auth.SessionRepository;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.auth.UserStatus;

import lombok.RequiredArgsConstructor;

/** 예매 단계별 상태를 만드는 도우미 */
@TestComponent
@RequiredArgsConstructor
public class BookingTestHelper {

    private final TestFixtures fixtures;
    private final OpenBookingService openBookingService;
    private final OpenEventRepository openEventRepository;
    private final BookingService bookingService;
    private final SessionRepository sessionRepository;

    private int userSequence = 0;

    /** 구역 A, B (각각 2x2, 마지막 칸은 빈 칸)를 가진 장소의 이벤트를 만들고 예매를 연다. */
    public Event openedEvent(int maxSize) {
        LocalDateTime now = LocalDateTime.now();
        Event event = fixtures.event(fixtures.program("맘마미아", fixtures.place("대극장", "A", "B")),
                now.minusHours(1), now.plusDays(1), now.plusDays(2));
        openBookingService.open(event.getId());
        openEventRepository.setMaxSize(event.getId(), maxSize);
        return event;
    }

    /** DB 사용자와 LOGIN 세션 */
    public SessionUser loginUser() {
        userSequence++;
        User user = fixtures.member("booker" + userSequence);
        return sessionRepository.create(user.getId(), user.getLoginId(), UserStatus.LOGIN);
    }

    public SessionUser refresh(SessionUser session) {
        return sessionRepository.findBySid(session.sid()).orElseThrow();
    }

    /** 입장 요청까지 (자리가 있으면 ENTERING, 없으면 WAITING) */
    public SessionUser requestAdmission(long eventId) {
        SessionUser user = loginUser();
        bookingService.requestAdmission(user, eventId);
        return refresh(user);
    }

    /** 좌석 선택 중(SELECTING_SEAT)까지. 좌석 SSE 연결은 서비스 호출로 대신한다. */
    public SessionUser selectingSeat(long eventId, int bookingAmount) {
        SessionUser user = requestAdmission(eventId);
        bookingService.setBookingAmount(user, bookingAmount);
        bookingService.enterSeatSelection(user, eventId);
        return refresh(user);
    }

    public void setBookingAmount(SessionUser user, int amount) {
        bookingService.setBookingAmount(user, amount);
    }

    public void reserve(SessionUser user, long eventId, Seat seat) {
        bookingService.updateSeat(user, eventId, seat, SeatStatus.RESERVE);
    }

    public SessionUser selectingSeatWith(long eventId, Seat... seats) {
        SessionUser user = selectingSeat(eventId, seats.length);
        for (Seat seat : seats) {
            bookingService.updateSeat(user, eventId, seat, SeatStatus.RESERVE);
        }
        return user;
    }
}
