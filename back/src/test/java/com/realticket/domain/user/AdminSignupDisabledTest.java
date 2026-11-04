package com.realticket.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.realticket.domain.user.repository.UserRepository;
import com.realticket.support.IntegrationTest;

/** 운영 설정(app.admin-signup-enabled=false)에서는 관리자 회원가입을 막는다. */
@TestPropertySource(properties = "app.admin-signup-enabled=false")
class AdminSignupDisabledTest extends IntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Test
    void 비활성화되어_있으면_404이고_계정을_만들지_않는다() throws Exception {
        mockMvc.perform(post("/user/signup/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "admin01", "loginPassword", "pass1234"))))
                .andExpect(status().isNotFound());

        assertThat(userRepository.findByLoginId("admin01")).isEmpty();
    }

    @Test
    void 일반_회원가입은_그대로_동작한다() throws Exception {
        mockMvc.perform(post("/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "user01", "loginPassword", "pass1234"))))
                .andExpect(status().isCreated());
    }
}
