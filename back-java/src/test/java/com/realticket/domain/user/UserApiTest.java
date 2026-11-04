package com.realticket.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import com.realticket.domain.event.entity.Event;
import com.realticket.domain.user.entity.User;
import com.realticket.domain.user.entity.UserRole;
import com.realticket.domain.user.repository.UserRepository;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.auth.UserStatus;
import com.realticket.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

class UserApiTest extends IntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        void 비밀번호를_암호화해서_저장한다() throws Exception {
            mockMvc.perform(post("/user/signup")
                            .contentType(MediaType.APPLICATION_JSON).content(credentials("user01", "pass1234")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("회원가입이 성공적으로 완료되었습니다."));

            User user = userRepository.findByLoginId("user01").orElseThrow();
            assertThat(user.getRole()).isEqualTo(UserRole.USER);
            assertThat(user.isGuest()).isFalse();
            assertThat(user.getLoginPassword()).isNotEqualTo("pass1234");
            assertThat(passwordEncoder.matches("pass1234", user.getLoginPassword())).isTrue();
        }

        @Test
        void 관리자_회원가입은_ADMIN_권한으로_저장한다() throws Exception {
            mockMvc.perform(post("/user/signup/admin")
                            .contentType(MediaType.APPLICATION_JSON).content(credentials("admin01", "pass1234")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("관리자 회원가입이 성공적으로 완료되었습니다."));

            assertThat(userRepository.findByLoginId("admin01").orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
        }

        @Test
        void 이미_있는_아이디면_409() throws Exception {
            signup("user01", "pass1234");

            mockMvc.perform(post("/user/signup")
                            .contentType(MediaType.APPLICATION_JSON).content(credentials("user01", "other123")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("이미 존재하는 사용자입니다."));
        }

        @Test
        void 아이디_형식이_틀리면_400() throws Exception {
            for (String invalid : new String[] {"abc", "UPPER01", "toolongid12345", "한글아이디"}) {
                mockMvc.perform(post("/user/signup")
                                .contentType(MediaType.APPLICATION_JSON).content(credentials(invalid, "pass1234")))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isArray());
            }
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        void 성공하면_SID_쿠키와_아이디를_돌려준다() throws Exception {
            signup("user01", "pass1234");

            MvcResult result = mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON).content(credentials("user01", "pass1234")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginId").value("user01"))
                    .andExpect(cookie().exists("SID"))
                    .andExpect(cookie().httpOnly("SID", true))
                    .andExpect(cookie().path("SID", "/"))
                    .andReturn();

            SessionUser session = sessionRepository.findBySid(result.getResponse().getCookie("SID").getValue())
                    .orElseThrow();
            assertThat(session.loginId()).isEqualTo("user01");
            assertThat(session.status()).isEqualTo(UserStatus.LOGIN);
        }

        @Test
        void 관리자는_ADMIN_세션을_받는다() throws Exception {
            mockMvc.perform(post("/user/signup/admin")
                    .contentType(MediaType.APPLICATION_JSON).content(credentials("admin01", "pass1234")));

            String sid = login("admin01", "pass1234").getValue();

            assertThat(sessionRepository.findBySid(sid).orElseThrow().status()).isEqualTo(UserStatus.ADMIN);
        }

        @Test
        void 비밀번호가_틀리면_401() throws Exception {
            signup("user01", "pass1234");

            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON).content(credentials("user01", "wrong123")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("아이디/비밀번호를 잘못 입력하셨습니다. 다시 입력해주세요"));
        }

        @Test
        void 없는_아이디면_401() throws Exception {
            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON).content(credentials("nobody01", "pass1234")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 다시_로그인하면_이전_세션은_끊긴다() throws Exception {
            signup("user01", "pass1234");
            Cookie first = login("user01", "pass1234");
            Cookie second = login("user01", "pass1234");

            mockMvc.perform(get("/user").cookie(first)).andExpect(status().isForbidden());
            mockMvc.perform(get("/user").cookie(second)).andExpect(status().isOk());
        }

        @Test
        void 로그인에_실패해도_기존_세션은_유지된다() throws Exception {
            signup("user01", "pass1234");
            Cookie session = login("user01", "pass1234");

            mockMvc.perform(post("/user/login")
                    .contentType(MediaType.APPLICATION_JSON).content(credentials("user01", "wrong123")));

            mockMvc.perform(get("/user").cookie(session)).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("게스트")
    class Guest {

        @Test
        void 게스트_계정과_세션을_만든다() throws Exception {
            MvcResult result = mockMvc.perform(get("/user/guest"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.loginId").value(org.hamcrest.Matchers.startsWith("guest-")))
                    .andExpect(jsonPath("$.userStatus").value("LOGIN"))
                    .andExpect(jsonPath("$.targetEvent").doesNotExist())
                    .andExpect(cookie().httpOnly("SID", true))
                    .andReturn();

            assertThat(userRepository.findAll()).singleElement().satisfies(user -> {
                assertThat(user.isGuest()).isTrue();
                assertThat(user.getLoginPassword()).isNull();
            });
            mockMvc.perform(get("/user").cookie(result.getResponse().getCookie("SID")))
                    .andExpect(status().isOk());
        }

        @Test
        void 관리자는_게스트와_게스트의_예약을_모두_삭제한다() throws Exception {
            Event event = anEvent();
            User member = fixtures.member("member01");
            User guest = fixtures.guest("guest-1");
            fixtures.reservation(member, event, 2);
            fixtures.reservation(guest, event, 2);
            var deletedReservation = fixtures.reservation(guest, event, 1);
            // soft delete 된 예약도 외래 키 때문에 함께 지워져야 한다
            jdbcTemplate.update("UPDATE reservations SET deleted_at = NOW() WHERE id = ?", deletedReservation.getId());

            mockMvc.perform(delete("/user/guest").cookie(adminCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletedUsers").value(1))
                    .andExpect(jsonPath("$.deletedReservations").value(2))
                    .andExpect(jsonPath("$.deletedReservedSeats").value(3));

            assertThat(userRepository.findAll()).extracting(User::getLoginId).containsExactly("member01");
            assertThat(count("reservations")).isEqualTo(1);
            assertThat(count("reserved_seats")).isEqualTo(2);
        }

        @Test
        void 게스트_일괄_삭제는_관리자만_가능하다() throws Exception {
            mockMvc.perform(delete("/user/guest").cookie(loginCookie()))
                    .andExpect(status().isUnauthorized());
        }

        private Event anEvent() {
            LocalDateTime now = LocalDateTime.now();
            return fixtures.event(fixtures.program("맘마미아", fixtures.place("대극장", "A")),
                    now.minusDays(1), now.plusDays(1), now.plusDays(2));
        }

        private int count(String table) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        }
    }

    @Nested
    @DisplayName("아이디 중복 확인")
    class CheckId {

        @Test
        void 없는_아이디면_사용_가능() throws Exception {
            mockMvc.perform(post("/user/checkid")
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("loginId", "user01"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(true));
        }

        @Test
        void 있는_아이디면_사용_불가() throws Exception {
            signup("user01", "pass1234");

            mockMvc.perform(post("/user/checkid")
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("loginId", "user01"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false));
        }
    }

    @Nested
    @DisplayName("로그아웃과 내 정보")
    class LogoutAndInfo {

        @Test
        void 내_정보를_조회한다() throws Exception {
            signup("user01", "pass1234");

            mockMvc.perform(get("/user").cookie(login("user01", "pass1234")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginId").value("user01"));
        }

        @Test
        void 로그인하지_않았으면_내_정보는_403() throws Exception {
            mockMvc.perform(get("/user")).andExpect(status().isForbidden());
        }

        @Test
        void 로그아웃하면_세션과_쿠키가_지워진다() throws Exception {
            signup("user01", "pass1234");
            Cookie session = login("user01", "pass1234");

            mockMvc.perform(post("/user/logout").cookie(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("로그아웃 되었습니다."))
                    .andExpect(cookie().maxAge("SID", 0));

            assertThat(sessionRepository.findBySid(session.getValue())).isEmpty();
            mockMvc.perform(get("/user").cookie(session)).andExpect(status().isForbidden());
        }

        @Test
        void 로그인하지_않고_로그아웃하면_403() throws Exception {
            mockMvc.perform(post("/user/logout")).andExpect(status().isForbidden());
        }
    }

    private String credentials(String loginId, String password) throws Exception {
        return json(Map.of("loginId", loginId, "loginPassword", password));
    }

    private void signup(String loginId, String password) throws Exception {
        mockMvc.perform(post("/user/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(loginId, password)))
                .andExpect(status().isCreated());
    }

    private Cookie login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(loginId, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SID");
    }
}
