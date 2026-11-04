package com.realticket.global.exception;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * 모든 에러 응답을 {@link ErrorResponse} 형식으로 맞춘다.
 * SSE 요청(Accept: text/event-stream)에서도 JSON으로 내려가도록 Content-Type을 명시한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException e) {
        if (e.getStatus().is5xxServerError()) {
            log.error("[ApiException] {}", e.getMessage(), e);
        }
        return body(e.getStatus(), ErrorResponse.of(e.getStatus(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = Number.class.isAssignableFrom(boxed(e.getRequiredType()))
                ? "Validation failed (numeric string is expected)"
                : "Validation failed (" + e.getName() + ")";
        return body(HttpStatus.BAD_REQUEST, ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception e) {
        log.error("[UnexpectedException] {}", e.getMessage(), e);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return body(status, ErrorResponse.of(status, "Internal server error"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> messages = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError
                        ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                        : error.getDefaultMessage())
                .toList();
        return body(status, ErrorResponse.of(status, messages));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> messages = ex.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .toList();
        return body(status, ErrorResponse.of(status, messages));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = "Cannot " + ex.getHttpMethod() + " /" + ex.getResourcePath();
        return body(status, ErrorResponse.of(status, message));
    }

    /** 그 밖의 Spring MVC 표준 예외(지원하지 않는 메서드, 본문 파싱 실패 등) */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest
                && servletRequest.getResponse() != null
                && servletRequest.getResponse().isCommitted()) {
            return null;
        }
        String message = ex.getMessage() != null ? ex.getMessage() : HttpStatus.valueOf(statusCode.value()).name();
        return body(statusCode, ErrorResponse.of(statusCode, message));
    }

    private ResponseEntity<Object> body(HttpStatusCode status, ErrorResponse errorResponse) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
    }

    private static Class<?> boxed(@Nullable Class<?> type) {
        if (type == null) {
            return Object.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        return type;
    }
}
