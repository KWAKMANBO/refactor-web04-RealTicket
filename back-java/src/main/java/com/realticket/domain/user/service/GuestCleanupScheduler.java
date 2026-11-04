package com.realticket.domain.user.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 매일 자정(한국 시간)에 게스트 계정을 정리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private final UserService userService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void removeAllGuests() {
        try {
            userService.removeAllGuests();
        } catch (Exception e) {
            log.error("게스트 정리 실패", e);
        }
    }
}
