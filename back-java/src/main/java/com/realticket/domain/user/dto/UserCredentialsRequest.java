package com.realticket.domain.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원가입·로그인 요청. 아이디/비밀번호 모두 영문 소문자+숫자 4~12자 */
public record UserCredentialsRequest(
        @NotNull @Size(min = 4, max = 12) @Pattern(regexp = "^[a-z0-9]+$") String loginId,
        @NotNull @Size(min = 4, max = 12) @Pattern(regexp = "^[a-z0-9]+$") String loginPassword) {
}
