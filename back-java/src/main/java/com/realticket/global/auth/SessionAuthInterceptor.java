package com.realticket.global.auth;

import java.util.Arrays;
import java.util.Comparator;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.realticket.global.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * {@link RequireStatus} 가 붙은 핸들러에 대해 세션과 진행 단계를 검사한다.
 * 통과하면 세션 만료 시간을 연장하고, {@link SessionUser} 를 요청 속성에 담아 둔다.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_ATTRIBUTE = SessionAuthInterceptor.class.getName() + ".SESSION";

    private final SessionRepository sessionRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireStatus requireStatus = findAnnotation(handlerMethod);
        if (requireStatus == null) {
            return true;
        }

        SessionUser session = sessionRepository.findBySid(SessionCookies.read(request))
                .orElseThrow(() -> ApiException.forbidden("접근 권한이 없습니다."));

        UserStatus minimum = Arrays.stream(requireStatus.value())
                .min(Comparator.naturalOrder())
                .orElse(UserStatus.LOGIN);
        if (!session.status().satisfies(minimum)) {
            throw ApiException.unauthorized("해당 페이지에 접근할 수 없습니다.");
        }

        sessionRepository.touch(session);
        request.setAttribute(SESSION_ATTRIBUTE, session);
        return true;
    }

    private RequireStatus findAnnotation(HandlerMethod handlerMethod) {
        RequireStatus onMethod = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(),
                RequireStatus.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireStatus.class);
    }
}
