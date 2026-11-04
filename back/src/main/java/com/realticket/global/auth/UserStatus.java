package com.realticket.global.auth;

/**
 * 세션의 진행 단계. level이 높을수록 더 많은 API에 접근할 수 있다.
 * 예) SELECTING_SEAT 사용자는 ENTERING 이상을 요구하는 API에 접근 가능.
 */
public enum UserStatus {
    LOGIN(0),
    WAITING(1),
    ENTERING(2),
    SELECTING_SEAT(3),
    ADMIN(4);

    private final int level;

    UserStatus(int level) {
        this.level = level;
    }

    public boolean satisfies(UserStatus required) {
        return this.level >= required.level;
    }
}
