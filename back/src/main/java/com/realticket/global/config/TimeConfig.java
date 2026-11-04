package com.realticket.global.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DB 에는 한국 시간(LocalDateTime)으로 저장하고, API 에서는 UTC ISO-8601(Instant, "...Z")로 주고받는다.
 * Nest 버전도 Date 를 "2024-12-01T06:00:00.000Z" 형식으로 내려주었으므로 프런트엔드 동작이 같다.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }

    public static Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZONE).toInstant();
    }

    public static LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZONE);
    }
}
