package com.realticket.domain.user.dto;

import com.realticket.global.auth.SessionUser;

/** 게스트 로그인 응답. Nest 버전과 같은 필드 (targetEvent 는 항상 null) */
public record GuestResponse(Long id, String loginId, String userStatus, Long targetEvent) {

    public static GuestResponse from(SessionUser session) {
        return new GuestResponse(session.userId(), session.loginId(), session.status().name(), null);
    }
}
