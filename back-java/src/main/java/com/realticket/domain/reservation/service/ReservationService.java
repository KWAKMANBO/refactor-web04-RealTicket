package com.realticket.domain.reservation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.realticket.domain.booking.service.BookingService;
import com.realticket.domain.booking.service.InBookingSnapshot;
import com.realticket.domain.booking.service.Seat;
import com.realticket.domain.event.entity.Event;
import com.realticket.domain.event.repository.EventRepository;
import com.realticket.domain.place.entity.Section;
import com.realticket.domain.place.service.PlaceService;
import com.realticket.domain.reservation.dto.ReservationCreateRequest;
import com.realticket.domain.reservation.dto.ReservationResponse;
import com.realticket.domain.reservation.dto.ReservationResultResponse;
import com.realticket.domain.reservation.entity.Reservation;
import com.realticket.domain.reservation.entity.ReservedSeat;
import com.realticket.domain.reservation.repository.ReservationRepository;
import com.realticket.domain.user.repository.UserRepository;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.config.TimeConfig;
import com.realticket.global.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PlaceService placeService;
    private final BookingService bookingService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 예매 확정. 요청한 좌석이 내가 선점한 좌석과 정확히 같아야 한다.
     * Redis 에 "저장 중" 표시를 먼저 해 같은 요청이 두 번 들어와도 한 번만 저장한다.
     * 저장에 실패하면 표시를 되돌려 다시 시도할 수 있게 한다.
     */
    public ReservationResultResponse create(SessionUser user, ReservationCreateRequest request) {
        Long eventId = user.targetEventId();
        if (eventId == null || !eventId.equals(request.eventId())) {
            throw ApiException.badRequest("예매 정보 또는 설정한 좌석수가 올바르지 않습니다.");
        }

        Set<Seat> requested = request.seats().stream()
                .map(ReservationCreateRequest.SeatItem::toSeat)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        InBookingSnapshot snapshot = bookingService.getInBooking(eventId, user.sid());
        if (requested.size() != request.seats().size() || !snapshot.bookedSeats().containsAll(requested)) {
            throw ApiException.badRequest("선점하지 않은 좌석이 포함되어 있습니다.");
        }
        if (requested.size() != snapshot.bookingAmount()) {
            throw ApiException.badRequest("예매 정보 또는 설정한 좌석수가 올바르지 않습니다.");
        }

        bookingService.markSaving(eventId, user.sid());
        try {
            return transactionTemplate.execute(status -> save(user.userId(), eventId, requested));
        } catch (RuntimeException e) {
            bookingService.cancelSaving(eventId, user.sid());
            if (e instanceof ApiException) {
                throw e;
            }
            log.error("예매 저장 실패 userId={}, eventId={}", user.userId(), eventId, e);
            throw ApiException.badRequest("예매에 실패했습니다.");
        }
    }

    public List<ReservationResponse> findUpcoming(SessionUser user) {
        List<Reservation> reservations = transactionTemplate.execute(status -> {
            List<Reservation> found = reservationRepository.findUpcomingByUserId(user.userId(), LocalDateTime.now(clock));
            found.forEach(reservation -> reservation.getReservedSeats().size()); // 좌석 미리 읽기
            return found;
        });
        return reservations.stream()
                .map(reservation -> new ReservationResponse(
                        reservation.getId(),
                        reservation.getProgram().getName(),
                        TimeConfig.toInstant(reservation.getEvent().getRunningDate()),
                        reservation.getEvent().getPlace().getName(),
                        String.join(", ", seatLabels(reservation.getReservedSeats()))))
                .toList();
    }

    /** 예매 취소. 예매가 열려 있는 이벤트라면 좌석을 다시 예약 가능하게 만든다. */
    public void cancel(SessionUser user, Long reservationId) {
        record Cancelled(long eventId, List<Seat> seats) {
        }
        Cancelled cancelled = transactionTemplate.execute(status -> {
            Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, user.userId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "사용자의 해당 예매 내역[" + reservationId + "]가 존재하지 않습니다."));
            List<Seat> seats = reservation.getReservedSeats().stream()
                    .map(seat -> new Seat(seat.getSectionIndex(), seat.seatIndex()))
                    .toList();
            reservation.softDelete(LocalDateTime.now(clock));
            return new Cancelled(reservation.getEvent().getId(), seats);
        });
        bookingService.freeSeats(cancelled.eventId(), cancelled.seats());
    }

    private ReservationResultResponse save(long userId, long eventId, Set<Seat> seats) {
        Event event = eventRepository.findWithProgramAndPlaceById(eventId)
                .orElseThrow(() -> ApiException.badRequest("해당 이벤트[" + eventId + "]가 존재하지 않습니다."));
        List<Section> sections = placeService.findSectionsInOrder(event.getPlace());

        Reservation reservation = Reservation.builder()
                .user(userRepository.getReferenceById(userId))
                .event(event)
                .program(event.getProgram())
                .createdAt(LocalDateTime.now(clock))
                .build();
        seats.stream()
                .sorted(Comparator.comparingInt(Seat::sectionIndex).thenComparingInt(Seat::seatIndex))
                .forEach(seat -> reservation.addReservedSeat(toReservedSeat(event, sections, seat)));
        reservationRepository.save(reservation);

        return new ReservationResultResponse(
                event.getProgram().getName(),
                TimeConfig.toInstant(event.getRunningDate()),
                event.getPlace().getName(),
                event.getProgram().getPrice(),
                seatLabels(reservation.getReservedSeats()));
    }

    private ReservedSeat toReservedSeat(Event event, List<Section> sections, Seat seat) {
        if (seat.sectionIndex() >= sections.size()) {
            throw ApiException.badRequest("해당 section이 존재하지 않습니다. sectionIndex: " + seat.sectionIndex());
        }
        Section section = sections.get(seat.sectionIndex());
        if (seat.seatIndex() >= section.getSeats().size()) {
            throw ApiException.badRequest("해당 section의 seat이 존재하지 않습니다. seatIndex: " + seat.seatIndex());
        }
        return ReservedSeat.builder()
                .event(event)
                .sectionName(section.getName())
                .sectionIndex(seat.sectionIndex())
                .colLen(section.getColLen())
                .row(seat.seatIndex() / section.getColLen() + 1) // 1행부터
                .col(seat.seatIndex() % section.getColLen() + 1) // 1열부터
                .build();
    }

    private static List<String> seatLabels(List<ReservedSeat> seats) {
        return seats.stream()
                .map(seat -> seat.getSectionName() + "구역 " + seat.getRow() + "행 " + seat.getCol() + "열")
                .toList();
    }
}
