package com.realticket.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.realticket.domain.booking.dto.AdmissionResponse;
import com.realticket.domain.booking.dto.SeatStatus;
import com.realticket.domain.booking.repository.AdmissionRepository;
import com.realticket.domain.booking.service.BookingService;
import com.realticket.domain.booking.service.Seat;
import com.realticket.domain.event.entity.Event;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.exception.ApiException;
import com.realticket.support.IntegrationTest;

/** Nest 버전에서 Node 단일 스레드에 가려져 있던 경쟁 조건을 멀티스레드에서 검증한다. */
class BookingConcurrencyTest extends IntegrationTest {

    @Autowired
    BookingService bookingService;

    @Autowired
    AdmissionRepository admissionRepository;

    @Test
    void 같은_좌석을_동시에_선점하면_한_명만_성공한다() throws Exception {
        Event event = booking.openedEvent(100);
        List<SessionUser> users = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            users.add(booking.selectingSeat(event.getId(), 1));
        }

        List<Boolean> results = runConcurrently(users.stream()
                .map(user -> (Callable<Boolean>) () -> succeeded(() ->
                        bookingService.updateSeat(user, event.getId(), new Seat(0, 0), SeatStatus.RESERVE)))
                .toList());

        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
    }

    @Test
    void 한_사람이_동시에_여러_좌석을_선점해도_설정한_인원을_넘지_않는다() throws Exception {
        Event event = booking.openedEvent(100);
        SessionUser user = booking.selectingSeat(event.getId(), 2);
        List<Seat> seats = List.of(new Seat(0, 0), new Seat(0, 1), new Seat(0, 2), new Seat(1, 0), new Seat(1, 1),
                new Seat(1, 2));

        List<Boolean> results = runConcurrently(seats.stream()
                .map(seat -> (Callable<Boolean>) () -> succeeded(() ->
                        bookingService.updateSeat(user, event.getId(), seat, SeatStatus.RESERVE)))
                .toList());

        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(2);
    }

    @Test
    void 동시에_입장을_요청해도_정원만큼만_입장하고_나머지는_겹치지_않는_대기_번호를_받는다() throws Exception {
        Event event = booking.openedEvent(5);
        List<SessionUser> users = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            users.add(booking.loginUser());
        }

        List<AdmissionResponse> results = runConcurrently(users.stream()
                .map(user -> (Callable<AdmissionResponse>) () -> bookingService.requestAdmission(user, event.getId()))
                .toList());

        assertThat(results).filteredOn(AdmissionResponse::enteringStatus).hasSize(5);
        assertThat(results).filteredOn(AdmissionResponse::waitingStatus)
                .extracting(AdmissionResponse::userOrder)
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.LongStream.rangeClosed(1, 35).boxed().toList());
        assertThat(admissionRepository.enteringCount(event.getId())).isEqualTo(5);
    }

    @Test
    void 여러_명이_동시에_떠나도_빈자리보다_많이_입장시키지_않는다() throws Exception {
        Event event = booking.openedEvent(3);
        List<SessionUser> selecting = List.of(
                booking.selectingSeat(event.getId(), 1),
                booking.selectingSeat(event.getId(), 1),
                booking.selectingSeat(event.getId(), 1));
        for (int i = 0; i < 10; i++) {
            booking.requestAdmission(event.getId());
        }

        runConcurrently(selecting.stream()
                .map(user -> (Callable<Boolean>) () -> {
                    bookingService.leaveSeatSelection(event.getId(), user.sid());
                    return true;
                })
                .toList());

        assertThat(admissionRepository.enteringCount(event.getId())).isEqualTo(3);
        assertThat(admissionRepository.waitingCount(event.getId())).isEqualTo(7);
    }

    private boolean succeeded(Runnable action) {
        try {
            action.run();
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
