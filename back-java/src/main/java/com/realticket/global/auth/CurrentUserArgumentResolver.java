package com.realticket.global.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.realticket.global.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final SessionRepository sessionRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && SessionUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        Object cached = request.getAttribute(SessionAuthInterceptor.SESSION_ATTRIBUTE);
        if (cached instanceof SessionUser sessionUser) {
            return sessionUser;
        }

        SessionUser session = sessionRepository.findBySid(SessionCookies.read(request)).orElse(null);
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        if (session == null && annotation != null && annotation.required()) {
            throw ApiException.forbidden("접근 권한이 없습니다.");
        }
        return session;
    }
}
