package com.realticket.global.auth;

/**
 * Redis에 저장된 로그인 세션 정보.
 *
 * @param sid           SID 쿠키 값
 * @param userId        users.id
 * @param loginId       로그인 ID
 * @param status        진행 단계
 * @param targetEventId 예매 진행 중인 이벤트 id (없으면 null)
 */
public record SessionUser(String sid, long userId, String loginId, UserStatus status, Long targetEventId) {
}
