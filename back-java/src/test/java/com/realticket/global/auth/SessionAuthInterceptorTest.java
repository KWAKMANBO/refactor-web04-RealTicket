package com.realticket.global.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.realticket.TestcontainersConfiguration;
import com.realticket.global.exception.ApiException;

import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SessionAuthInterceptorTest.TestController.class})
class SessionAuthInterceptorTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SessionRepository sessionRepository;

    @Test
    void 세션이_없으면_403() throws Exception {
        mockMvc.perform(get("/test/login"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value(403))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void 단계가_부족하면_401() throws Exception {
        SessionUser session = sessionRepository.create(100L, "inter01", UserStatus.LOGIN);

        mockMvc.perform(get("/test/entering").cookie(new Cookie("SID", session.sid())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("해당 페이지에 접근할 수 없습니다."));
    }

    @Test
    void 요구_단계_이상이면_통과하고_세션이_주입된다() throws Exception {
        SessionUser session = sessionRepository.create(101L, "inter02", UserStatus.SELECTING_SEAT);

        mockMvc.perform(get("/test/entering").cookie(new Cookie("SID", session.sid())))
                .andExpect(status().isOk())
                .andExpect(content().string("inter02"));
    }

    @Test
    void ADMIN은_모든_단계를_통과한다() throws Exception {
        SessionUser admin = sessionRepository.create(102L, "inter03", UserStatus.ADMIN);

        mockMvc.perform(get("/test/entering").cookie(new Cookie("SID", admin.sid())))
                .andExpect(status().isOk());
    }

    @Test
    void SSE_요청에서도_에러는_JSON으로_내려간다() throws Exception {
        mockMvc.perform(get("/test/login").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.statusCode").value(403));
    }

    @Test
    void ApiException은_Nest_형식으로_변환된다() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value(409))
                .andExpect(jsonPath("$.message").value("이미 존재합니다."))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void 검증_실패는_400과_메시지_배열() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isArray())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void 숫자_경로변수_파싱_실패는_400() throws Exception {
        mockMvc.perform(get("/test/number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed (numeric string is expected)"));
    }

    @Test
    void 없는_경로는_404() throws Exception {
        mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @RestController
    static class TestController {

        @RequireStatus
        @GetMapping("/test/login")
        String login(@CurrentUser SessionUser user) {
            return user.loginId();
        }

        @RequireStatus({UserStatus.ENTERING, UserStatus.SELECTING_SEAT})
        @GetMapping("/test/entering")
        String entering(@CurrentUser SessionUser user) {
            return user.loginId();
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw ApiException.conflict("이미 존재합니다.");
        }

        @PostMapping("/test/validate")
        String validate(@Valid @RequestBody AmountRequest request) {
            return "ok";
        }

        @GetMapping("/test/number/{id}")
        String number(@PathVariable long id) {
            return String.valueOf(id);
        }

        record AmountRequest(@Min(1) int amount) {
        }
    }
}
