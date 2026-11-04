package com.realticket.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param frontUrl           CORS 허용 Origin
 * @param adminSignupEnabled 관리자 회원가입 API(POST /user/signup/admin) 사용 여부.
 *                           누구나 관리자 계정을 만들 수 있으므로 운영 환경에서는 끈다.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontUrl, boolean adminSignupEnabled) {
}
