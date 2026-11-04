package com.realticket.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realticket.TestcontainersConfiguration;
import com.realticket.global.auth.SessionRepository;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.auth.UserStatus;

import jakarta.servlet.http.Cookie;

/**
 * 통합 테스트 공통 설정. 모든 테스트가 같은 Spring 컨텍스트(= 같은 MySQL/Redis 컨테이너)를 재사용하도록
 * 설정을 한 곳에 모아 둔다. 각 테스트 전에 DB 테이블과 Redis 를 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestFixtures.class, BookingTestHelper.class})
public abstract class IntegrationTest {

    private static final String[] TABLES = {
            "reserved_seats", "reservations", "events", "programs", "sections", "places", "users"};

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected SessionRepository sessionRepository;

    @Autowired
    protected TestFixtures fixtures;

    @Autowired
    protected BookingTestHelper booking;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    private long sessionSequence = 0;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : TABLES) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        redis.execute(connection -> {
            connection.serverCommands().flushDb();
            return null;
        }, true);
    }

    protected SessionUser session(UserStatus status) {
        sessionSequence++;
        return sessionRepository.create(sessionSequence, "tester" + sessionSequence, status);
    }

    protected Cookie cookieOf(UserStatus status) {
        return new Cookie("SID", session(status).sid());
    }

    protected Cookie adminCookie() {
        return cookieOf(UserStatus.ADMIN);
    }

    protected Cookie loginCookie() {
        return cookieOf(UserStatus.LOGIN);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
