package com.realticket.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.realticket.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SessionRepositoryTest {

    @Autowired
    SessionRepository sessionRepository;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void 세션을_만들고_조회한다() {
        SessionUser created = sessionRepository.create(1L, "create01", UserStatus.LOGIN);

        SessionUser found = sessionRepository.findBySid(created.sid()).orElseThrow();

        assertThat(found).isEqualTo(created);
        assertThat(redis.getExpire("user:" + created.sid())).isPositive();
    }

    @Test
    void 같은_계정으로_다시_로그인하면_이전_세션은_삭제된다() {
        SessionUser first = sessionRepository.create(2L, "relogin01", UserStatus.LOGIN);
        SessionUser second = sessionRepository.create(2L, "relogin01", UserStatus.LOGIN);

        assertThat(sessionRepository.findBySid(first.sid())).isEmpty();
        assertThat(sessionRepository.findBySid(second.sid())).isPresent();
    }

    @Test
    void 상태를_바꾼다() {
        SessionUser session = sessionRepository.create(3L, "status01", UserStatus.LOGIN);

        boolean updated = sessionRepository.updateStatus(session.sid(), UserStatus.WAITING);

        assertThat(updated).isTrue();
        assertThat(sessionRepository.findBySid(session.sid()).orElseThrow().status())
                .isEqualTo(UserStatus.WAITING);
    }

    @Test
    void ADMIN_세션의_상태는_바꾸지_않는다() {
        SessionUser admin = sessionRepository.create(4L, "admin01", UserStatus.ADMIN);

        boolean updated = sessionRepository.updateStatus(admin.sid(), UserStatus.LOGIN);

        assertThat(updated).isFalse();
        assertThat(sessionRepository.findBySid(admin.sid()).orElseThrow().status())
                .isEqualTo(UserStatus.ADMIN);
    }

    @Test
    void 만료된_세션은_갱신하지_않고_다시_만들지도_않는다() {
        String sid = "expired-sid";

        assertThat(sessionRepository.updateStatus(sid, UserStatus.WAITING)).isFalse();
        assertThat(sessionRepository.updateTargetEvent(sid, 1L)).isFalse();
        assertThat(redis.hasKey("user:" + sid)).isFalse();
    }

    @Test
    void 예매_대상_이벤트를_설정하고_비운다() {
        SessionUser session = sessionRepository.create(5L, "target01", UserStatus.LOGIN);

        sessionRepository.updateTargetEvent(session.sid(), 10L);
        assertThat(sessionRepository.findBySid(session.sid()).orElseThrow().targetEventId()).isEqualTo(10L);

        sessionRepository.updateTargetEvent(session.sid(), null);
        assertThat(sessionRepository.findBySid(session.sid()).orElseThrow().targetEventId()).isNull();
    }

    @Test
    void 세션을_삭제한다() {
        SessionUser session = sessionRepository.create(6L, "delete01", UserStatus.LOGIN);

        assertThat(sessionRepository.delete(session.sid(), session.loginId())).isTrue();
        assertThat(sessionRepository.findBySid(session.sid())).isEmpty();
        assertThat(redis.hasKey("user-id:" + session.loginId())).isFalse();
    }
}
