package com.realticket.domain.booking.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.realticket.domain.booking.config.BookingProperties;

import lombok.RequiredArgsConstructor;

/** enteringGcInterval 마다 만료된 입장 세션을 정리하고 대기자를 입장시킨다. */
@Component
@RequiredArgsConstructor
public class EnteringGcScheduler {

    private final BookingService bookingService;
    private final TaskScheduler taskScheduler;
    private final BookingProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        taskScheduler.scheduleWithFixedDelay(bookingService::collectExpiredEntering, properties.enteringGcInterval());
    }
}
