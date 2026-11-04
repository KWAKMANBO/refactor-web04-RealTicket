package com.realticket.global.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인 세션과 최소 진행 단계를 요구한다. NestJS의 {@code @UseGuards(SessionAuthGuard(...))}에 대응한다.
 * 여러 값을 주면 그중 가장 낮은 단계 이상이면 통과한다.
 * <ul>
 *     <li>세션 없음 → 403 "접근 권한이 없습니다."</li>
 *     <li>단계 부족 → 401 "해당 페이지에 접근할 수 없습니다."</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireStatus {

    UserStatus[] value() default UserStatus.LOGIN;
}
