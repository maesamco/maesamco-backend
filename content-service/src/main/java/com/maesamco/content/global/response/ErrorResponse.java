package com.maesamco.content.global.response;

import com.maesamco.content.global.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 에러 응답 공통 포맷 (게이트웨이 및 인증 보안 설계 9절).
 * Gateway 레벨 에러(401/403/429/503)도 이 포맷과 동일하게 맞춘다.
 *
 * { "success": false, "error": {"code": "...", "message": "..."}, "timestamp": "..." }
 */
public record ErrorResponse(boolean success, ErrorDetail error, LocalDateTime timestamp) {

    public record ErrorDetail(String code, String message, List<FieldError> fieldErrors) {
    }

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                false,
                new ErrorDetail(errorCode.name(), errorCode.getMessage(), null),
                LocalDateTime.now()
        );
    }

    public static ErrorResponse from(ErrorCode errorCode, String message) {
        return new ErrorResponse(
                false,
                new ErrorDetail(errorCode.name(), message, null),
                LocalDateTime.now()
        );
    }

    public static ErrorResponse from(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(
                false,
                new ErrorDetail(errorCode.name(), errorCode.getMessage(), fieldErrors),
                LocalDateTime.now()
        );
    }

    /** Gateway(WebFlux)처럼 ErrorCode enum 없이 코드 문자열만 있는 경우용. */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, new ErrorDetail(code, message, null), LocalDateTime.now());
    }
}
