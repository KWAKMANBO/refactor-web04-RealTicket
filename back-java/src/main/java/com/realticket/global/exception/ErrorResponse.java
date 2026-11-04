package com.realticket.global.exception;

import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;

/**
 * NestJS 기본 에러 응답과 같은 형식.
 * <pre>
 * { "statusCode": 409, "message": "이미 존재하는 사용자입니다.", "error": "Conflict" }
 * { "statusCode": 400, "message": ["loginId: ..."], "error": "Bad Request" }  // 검증 실패
 * </pre>
 * 프런트엔드가 이 형식에 맞춰 동작하므로 바꾸지 않는다.
 */
public record ErrorResponse(int statusCode, Object message, String error) {

    public static ErrorResponse of(HttpStatusCode status, String message) {
        return new ErrorResponse(status.value(), message, reasonPhrase(status));
    }

    public static ErrorResponse of(HttpStatusCode status, List<String> messages) {
        return new ErrorResponse(status.value(), messages, reasonPhrase(status));
    }

    private static String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }
}
