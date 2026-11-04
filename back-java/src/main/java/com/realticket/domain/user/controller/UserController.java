package com.realticket.domain.user.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.realticket.domain.user.dto.GuestCleanupResponse;
import com.realticket.domain.user.dto.GuestResponse;
import com.realticket.domain.user.dto.LoginIdCheckRequest;
import com.realticket.domain.user.dto.LoginIdCheckResponse;
import com.realticket.domain.user.dto.MessageResponse;
import com.realticket.domain.user.dto.UserCredentialsRequest;
import com.realticket.domain.user.dto.UserInfoResponse;
import com.realticket.domain.user.entity.UserRole;
import com.realticket.domain.user.service.UserService;
import com.realticket.global.auth.CurrentUser;
import com.realticket.global.auth.RequireStatus;
import com.realticket.global.auth.SessionCookies;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.auth.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "User")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse signup(@Valid @RequestBody UserCredentialsRequest request) {
        userService.register(request, UserRole.USER);
        return new MessageResponse("회원가입이 성공적으로 완료되었습니다.");
    }

    @Operation(summary = "관리자 회원가입")
    @PostMapping("/signup/admin")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse signupAdmin(@Valid @RequestBody UserCredentialsRequest request) {
        userService.register(request, UserRole.ADMIN);
        return new MessageResponse("관리자 회원가입이 성공적으로 완료되었습니다.");
    }

    @Operation(summary = "로그인", description = "성공하면 SID 쿠키를 발급한다.")
    @PostMapping("/login")
    public ResponseEntity<UserInfoResponse> login(@Valid @RequestBody UserCredentialsRequest request) {
        SessionUser session = userService.login(request.loginId(), request.loginPassword());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.create(session.sid()).toString())
                .body(new UserInfoResponse(session.loginId()));
    }

    @Operation(summary = "게스트 로그인", description = "게스트 계정을 만들고 SID 쿠키를 발급한다.")
    @GetMapping("/guest")
    public ResponseEntity<GuestResponse> loginAsGuest() {
        SessionUser session = userService.loginAsGuest();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.create(session.sid()).toString())
                .body(GuestResponse.from(session));
    }

    @Operation(summary = "게스트 계정 일괄 삭제 [관리자]")
    @RequireStatus(UserStatus.ADMIN)
    @DeleteMapping("/guest")
    public GuestCleanupResponse removeAllGuests() {
        return userService.removeAllGuests();
    }

    @Operation(summary = "아이디 중복 확인")
    @PostMapping("/checkid")
    public LoginIdCheckResponse checkLoginId(@Valid @RequestBody LoginIdCheckRequest request) {
        return new LoginIdCheckResponse(userService.isAvailableLoginId(request.loginId()));
    }

    @Operation(summary = "로그아웃", description = "세션을 지우고 SID 쿠키를 만료시킨다.")
    @RequireStatus
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@CurrentUser SessionUser session) {
        userService.logout(session);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.expire().toString())
                .body(new MessageResponse("로그아웃 되었습니다."));
    }

    @Operation(summary = "내 정보 조회")
    @RequireStatus
    @GetMapping
    public UserInfoResponse getUserInfo(@CurrentUser SessionUser session) {
        return new UserInfoResponse(session.loginId());
    }
}
