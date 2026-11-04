package com.realticket.domain.booking.controller;

import java.time.Clock;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.realticket.domain.booking.dto.AdmissionResponse;
import com.realticket.domain.booking.dto.BookingAmountRequest;
import com.realticket.domain.booking.dto.BookingAmountResponse;
import com.realticket.domain.booking.dto.MaxSizeRequest;
import com.realticket.domain.booking.dto.MaxSizeResponse;
import com.realticket.domain.booking.dto.SeatRequest;
import com.realticket.domain.booking.dto.SeatResponse;
import com.realticket.domain.booking.dto.ServerTimeResponse;
import com.realticket.domain.booking.service.BookingService;
import com.realticket.domain.booking.service.OpenBookingService;
import com.realticket.global.auth.CurrentUser;
import com.realticket.global.auth.RequireStatus;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.auth.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Booking")
@RestController
@RequestMapping("/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final OpenBookingService openBookingService;
    private final Clock clock;

    @Operation(summary = "예매 입장 요청", description = "자리가 있으면 바로 입장(ENTERING), 없으면 대기열(WAITING)에 들어간다.")
    @RequireStatus
    @GetMapping("/permission/{eventId}")
    public AdmissionResponse requestAdmission(@CurrentUser SessionUser user, @PathVariable long eventId) {
        return bookingService.requestAdmission(user, eventId);
    }

    @Operation(summary = "대기열 현황 SSE")
    @RequireStatus(UserStatus.WAITING)
    @GetMapping(value = "/re-permission/{eventId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeWaiting(@CurrentUser SessionUser user, @PathVariable long eventId) {
        return bookingService.subscribeWaiting(user, eventId);
    }

    @Operation(summary = "실시간 좌석 현황 SSE", description = "연결하면 좌석 선택 중(SELECTING_SEAT)이 되고, 연결이 끊기면 선점 좌석이 풀린다.")
    @RequireStatus({UserStatus.ENTERING, UserStatus.SELECTING_SEAT})
    @GetMapping(value = "/seat/{eventId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeSeats(@CurrentUser SessionUser user, @PathVariable long eventId) {
        return bookingService.enterSeatSelection(user, eventId);
    }

    @Operation(summary = "예매 인원 설정", description = "좌석 선택 중이면 이미 선점한 좌석이 모두 풀린다.")
    @RequireStatus({UserStatus.ENTERING, UserStatus.SELECTING_SEAT})
    @PostMapping("/count")
    public BookingAmountResponse setBookingAmount(@CurrentUser SessionUser user,
            @Valid @RequestBody BookingAmountRequest request) {
        return new BookingAmountResponse(bookingService.setBookingAmount(user, request.bookingAmount()));
    }

    @Operation(summary = "좌석 선점/취소", description = "expectedStatus: reserved(선점) / deleted(취소)")
    @RequireStatus(UserStatus.SELECTING_SEAT)
    @PostMapping
    public SeatResponse updateSeat(@CurrentUser SessionUser user, @Valid @RequestBody SeatRequest request) {
        return bookingService.updateSeat(user, request.eventId(), request.seat(), request.expectedStatus());
    }

    @Operation(summary = "서버 시간 조회")
    @RequireStatus
    @GetMapping("/server-time")
    public ServerTimeResponse serverTime() {
        return new ServerTimeResponse(clock.millis());
    }

    @Operation(summary = "좌석 선택 화면 최대 인원 설정 [관리자]")
    @RequireStatus(UserStatus.ADMIN)
    @PostMapping("/in-booking-pool-size/event/{eventId}")
    public MaxSizeResponse setMaxSize(@PathVariable long eventId, @Valid @RequestBody MaxSizeRequest request) {
        return new MaxSizeResponse(bookingService.setMaxSize(eventId, request.maxSize()));
    }

    @Operation(summary = "좌석 선택 화면 최대 인원 설정 - 열린 모든 이벤트 [관리자]")
    @RequireStatus(UserStatus.ADMIN)
    @PostMapping("/in-booking-pool-size/all")
    public MaxSizeResponse setMaxSizeOfAll(@Valid @RequestBody MaxSizeRequest request) {
        return new MaxSizeResponse(bookingService.setMaxSizeOfAllOpened(request.maxSize()));
    }

    @Operation(summary = "좌석 선택 화면 최대 인원 기본값 설정 [관리자]", description = "이후 오픈되는 이벤트에 적용된다.")
    @RequireStatus(UserStatus.ADMIN)
    @PostMapping("/in-booking-pool-size/default")
    public MaxSizeResponse setDefaultMaxSize(@Valid @RequestBody MaxSizeRequest request) {
        return new MaxSizeResponse(bookingService.setDefaultMaxSize(request.maxSize()));
    }

    @Operation(summary = "오픈 대상 이벤트 다시 확인 [관리자]")
    @RequireStatus(UserStatus.ADMIN)
    @PostMapping("/reload-open-target")
    public void reloadOpenTarget() {
        openBookingService.scheduleUpcomingReservations();
    }

    @Operation(summary = "예매 상태 초기화 [관리자]", description = "대기열, 입장, 좌석 선택, 좌석 상태를 모두 지우고 다시 연다.")
    @RequireStatus(UserStatus.ADMIN)
    @PostMapping("/init/{eventId}")
    public void reset(@PathVariable long eventId) {
        openBookingService.reset(eventId);
    }
}
