package com.realticket.global.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class SessionCookies {

    public static final String NAME = "SID";

    private SessionCookies() {
    }

    public static String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, NAME);
        return cookie != null && !cookie.getValue().isBlank() ? cookie.getValue() : null;
    }

    public static ResponseCookie create(String sid) {
        return ResponseCookie.from(NAME, sid)
                .httpOnly(true)
                .path("/")
                .build();
    }

    public static ResponseCookie expire() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
    }
}
