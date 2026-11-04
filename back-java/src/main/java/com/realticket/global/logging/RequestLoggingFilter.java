package com.realticket.global.logging;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/** "METHOD URI STATUS - Nms" 형식의 요청 로그 (Nest loggerMiddleware 대응) */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            String uri = request.getQueryString() != null
                    ? request.getRequestURI() + "?" + request.getQueryString()
                    : request.getRequestURI();
            log.info("{} {} {} - {}ms", request.getMethod(), uri, response.getStatus(),
                    System.currentTimeMillis() - start);
        }
    }
}
