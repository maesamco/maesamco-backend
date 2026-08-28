package com.maesamco.user.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 서비스 공통 에러코드 + 도메인별 에러코드를 함께 담는 enum.
 * 이 파일은 "템플릿"입니다 — 각 서비스에 복사한 뒤 // {service} 섹션에
 * 해당 서비스의 도메인 에러코드를 추가하세요.
 *
 * 규칙(팀 컨벤션 12절):
 *  - 도메인명_에러타입 형태로 명명하지 않고, 상수명 자체가 응답의 code 필드값이 된다 (name())
 *  - 다른 사용자의 리소스에 접근하는 경우 별도 코드를 만들지 않고
 *    존재하지 않는 리소스와 동일하게 404 + {DOMAIN}_NOT_FOUND 로 응답한다.
 */
public enum ErrorCode {

    // ===== common =====
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

    // ===== auth =====
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "무효화된 토큰입니다."),
    AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // ===== 서비스 간 통신 =====
    FEIGN_CLIENT_ERROR(HttpStatus.BAD_GATEWAY, "서비스 간 통신 중 오류가 발생했습니다."),
    INTERNAL_CALL_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "내부 호출 서명이 유효하지 않습니다."),
    INTERNAL_CALL_TIMESTAMP_EXPIRED(HttpStatus.UNAUTHORIZED, "내부 호출 요청이 만료되었습니다(재전송 의심).");

    // 이 아래에 서비스별 섹션을 추가하세요. 예)
    // ===== user =====
    // USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    //
    // ===== judge =====
    // SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "제출을 찾을 수 없습니다."),
    // IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "동일한 키로 다른 요청이 이미 처리되었습니다."),
    //
    // ===== coaching =====
    // HINT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "본인의 오답 제출에만 힌트를 요청할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
