package com.realticket.domain.booking.repository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.realticket.domain.booking.service.Seat;

import lombok.RequiredArgsConstructor;

/** 좌석 선택 중(in-booking)인 사용자의 세션과 선점 좌석 */
@Repository
@RequiredArgsConstructor
public class InBookingRepository {

    private final StringRedisTemplate redis;

    /** @return 1 옮김, 0 이미 좌석 선택 중, -1 입장 허가 없음 */
    public long moveFromEntering(long eventId, String sid) {
        return redis.execute(BookingScripts.MOVE_TO_IN_BOOKING, List.of(
                        BookingKeys.entering(eventId),
                        BookingKeys.enteringAmounts(eventId),
                        BookingKeys.inBookingSessions(eventId),
                        BookingKeys.inBookingSession(eventId, sid)),
                sid);
    }

    public boolean isInBooking(long eventId, String sid) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(BookingKeys.inBookingSessions(eventId), sid));
    }

    public long count(long eventId) {
        Long size = redis.opsForSet().size(BookingKeys.inBookingSessions(eventId));
        return size != null ? size : 0;
    }

    public Set<String> findSids(long eventId) {
        Set<String> sids = redis.opsForSet().members(BookingKeys.inBookingSessions(eventId));
        return sids != null ? sids : Set.of();
    }

    /** @return 1 성공, 0 이미 예약된 좌석, -1 좌석 선택 중 아님, -2 이미 예매 완료, -3 인원 초과, -4 없는 좌석 */
    public long reserveSeat(long eventId, String sid, Seat seat) {
        return redis.execute(BookingScripts.RESERVE_SEAT, List.of(
                        BookingKeys.sectionSeats(eventId, seat.sectionIndex()),
                        BookingKeys.sectionSeatsLen(eventId, seat.sectionIndex()),
                        BookingKeys.inBookingSession(eventId, sid),
                        BookingKeys.inBookingSeats(eventId, sid)),
                String.valueOf(seat.seatIndex()), seat.member());
    }

    /** @return 1 성공, -1 좌석 선택 중 아님, -2 이미 예매 완료, -5 선점하지 않은 좌석 */
    public long releaseSeat(long eventId, String sid, Seat seat) {
        return redis.execute(BookingScripts.RELEASE_SEAT, List.of(
                        BookingKeys.sectionSeats(eventId, seat.sectionIndex()),
                        BookingKeys.inBookingSession(eventId, sid),
                        BookingKeys.inBookingSeats(eventId, sid)),
                String.valueOf(seat.seatIndex()), seat.member());
    }

    /** @return 풀어 준 좌석 수, -1 좌석 선택 중 아님, -2 이미 예매 완료 */
    public long resetBookingAmount(long eventId, String sid, int amount) {
        return redis.execute(BookingScripts.RESET_BOOKING_AMOUNT, List.of(
                        BookingKeys.inBookingSession(eventId, sid),
                        BookingKeys.inBookingSeats(eventId, sid)),
                String.valueOf(amount), BookingKeys.sectionPrefix(eventId));
    }

    /** @return 풀어 준 좌석 수, -1 좌석 선택 중이 아니었음 */
    public long leave(long eventId, String sid) {
        return redis.execute(BookingScripts.LEAVE_IN_BOOKING, List.of(
                        BookingKeys.inBookingSessions(eventId),
                        BookingKeys.inBookingSession(eventId, sid),
                        BookingKeys.inBookingSeats(eventId, sid)),
                sid, BookingKeys.sectionPrefix(eventId));
    }

    /** @return 1 표시함, 0 이미 저장됨, -1 좌석 선택 중 아님 */
    public long markSaved(long eventId, String sid) {
        return redis.execute(BookingScripts.MARK_SAVED, List.of(BookingKeys.inBookingSession(eventId, sid)));
    }

    /** 예매 저장에 실패했을 때 되돌린다. */
    public void unmarkSaved(long eventId, String sid) {
        redis.opsForHash().put(BookingKeys.inBookingSession(eventId, sid), "saved", "0");
    }

    public int getBookingAmount(long eventId, String sid) {
        Object amount = redis.opsForHash().get(BookingKeys.inBookingSession(eventId, sid), "bookingAmount");
        return amount != null ? Integer.parseInt((String) amount) : 0;
    }

    public Set<Seat> findBookedSeats(long eventId, String sid) {
        Set<String> members = redis.opsForSet().members(BookingKeys.inBookingSeats(eventId, sid));
        return members == null ? Set.of()
                : members.stream().map(Seat::fromMember).collect(Collectors.toUnmodifiableSet());
    }

    /** 이벤트의 좌석 선택 세션을 모두 지운다. */
    public void deleteAll(long eventId) {
        Set<String> sids = findSids(eventId);
        List<String> keys = new java.util.ArrayList<>();
        for (String sid : sids) {
            keys.add(BookingKeys.inBookingSession(eventId, sid));
            keys.add(BookingKeys.inBookingSeats(eventId, sid));
        }
        keys.add(BookingKeys.inBookingSessions(eventId));
        redis.unlink(keys);
    }
}
