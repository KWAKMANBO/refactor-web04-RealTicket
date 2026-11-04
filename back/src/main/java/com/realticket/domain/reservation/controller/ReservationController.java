package com.realticket.domain.reservation.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.realticket.domain.reservation.dto.ReservationCreateRequest;
import com.realticket.domain.reservation.dto.ReservationResponse;
import com.realticket.domain.reservation.dto.ReservationResultResponse;
import com.realticket.domain.reservation.service.ReservationService;
import com.realticket.global.auth.CurrentUser;
import com.realticket.global.auth.RequireStatus;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.auth.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Reservation")
@RestController
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "내 예매 내역 조회", description = "아직 공연 전인 예매만 조회한다.")
    @RequireStatus
    @GetMapping
    public List<ReservationResponse> findMine(@CurrentUser SessionUser user) {
        return reservationService.findUpcoming(user);
    }

    @Operation(summary = "예매 취소")
    @RequireStatus
    @DeleteMapping("/{reservationId}")
    public void cancel(@CurrentUser SessionUser user, @PathVariable Long reservationId) {
        reservationService.cancel(user, reservationId);
    }

    @Operation(summary = "예매 확정", description = "선점한 좌석으로 예매를 저장한다.")
    @RequireStatus(UserStatus.SELECTING_SEAT)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResultResponse create(@CurrentUser SessionUser user,
            @Valid @RequestBody ReservationCreateRequest request) {
        return reservationService.create(user, request);
    }
}
