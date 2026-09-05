package com.maesamco.content.global.exception;

import org.springframework.http.HttpStatus;

/**
 * Content Service에서 두 명의 개발자가 작업하기에 충돌날 수도 있으니, 일단 ErrorCode는 따로 모으고, 얼추 확정되면 마저 합친다.
 */
public enum ErrorCode2 {

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
    INTERNAL_CALL_TIMESTAMP_EXPIRED(HttpStatus.UNAUTHORIZED, "내부 호출 요청이 만료되었습니다(재전송 의심)."),

    // ===== content =====
    LATEST_VERSION_NOT_FLAGGED(HttpStatus.CONFLICT, "수정할 수 없는 상태입니다."),
    INVALID_QUIZ_STATUS(HttpStatus.CONFLICT, "제출할 수 없는 상태입니다."),


    /* 여기서부터 따로 작성 */


    /* 여기서부터 따로 작성 */

    /* Problem */
    /** 요청한 문제를 찾을 수 없는 경우입니다. */
    PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "문제를 찾을 수 없습니다."),
    /** 이미 삭제된 문제를 다시 삭제하거나 접근하려는 경우입니다. */
    PROBLEM_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 문제입니다."),
    /** 허용되지 않은 문제 상태 변경을 요청한 경우입니다. */
    INVALID_PROBLEM_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "허용되지 않은 문제 상태 변경입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode2(HttpStatus status, String message) {
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
