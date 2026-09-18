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
    AUTH_REFRESH_TOKEN_REUSED(
            HttpStatus.UNAUTHORIZED,
            "Refresh Token 재사용이 감지되었습니다. 다시 로그인해주세요."
    ),
    AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),
    USER_NOT_ACTIVE(
            HttpStatus.FORBIDDEN,
            "현재 로그인할 수 없는 계정입니다."
    ),

    // ===== user =====
    USER_DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "이미 사용 중인 이메일입니다."
    ),
    USER_DUPLICATE_NICKNAME(
            HttpStatus.CONFLICT,
            "이미 사용 중인 닉네임입니다."
    ),
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "사용자를 찾을 수 없습니다."
    ),
    USER_CURRENT_PASSWORD_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "현재 비밀번호가 일치하지 않습니다."
    ),
    USER_PASSWORD_POLICY_VIOLATION(
            HttpStatus.BAD_REQUEST,
            "새 비밀번호가 비밀번호 정책을 만족하지 않습니다."
    ),
    USER_PASSWORD_CHANGE_CONFLICT(
            HttpStatus.CONFLICT,
            "비밀번호 변경 중 동시 수정이 감지되었습니다. 다시 시도해주세요."
    ),
    USER_PROFILE_UPDATE_CONFLICT(
            HttpStatus.CONFLICT,
            "사용자 정보 수정 중 동시 변경이 감지되었습니다. 다시 시도해주세요."
    ),
    EMAIL_VERIFICATION_INVALID_CODE(
            HttpStatus.BAD_REQUEST,
            "인증 코드가 올바르지 않습니다."
    ),
    EMAIL_VERIFICATION_EXPIRED(
            HttpStatus.BAD_REQUEST,
            "인증 코드가 만료되었습니다. 이메일 인증을 다시 요청해주세요."
    ),
    EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "인증 시도 횟수를 초과했습니다. 이메일 인증을 다시 요청해주세요."
    ),
    SIGNUP_VERIFICATION_TOKEN_INVALID(
            HttpStatus.BAD_REQUEST,
            "이메일 인증 정보가 유효하지 않습니다. 이메일 인증을 다시 진행해주세요."
    ),
    SIGNUP_AUTO_LOGIN_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "회원가입은 완료되었지만 자동 로그인에 실패했습니다. 로그인해주세요."
    ),
    GAMIFICATION_STATE_CONFLICT(
            HttpStatus.CONFLICT,
            "게이미피케이션 상태가 동시에 변경되었습니다. 다시 시도해주세요."
    ),
    // 회원가입 시 User와 UserGamificationState는 같은 트랜잭션에서 생성된다.
    // 따라서 사용자는 존재하지만 상태가 없는 경우는 정상 운영 중 발생하면 안 되는
    // 데이터 정합성 이상이다. 이슈 #223의 404 응답 계약은 유지하되,
    // 반복 재시도 대신 관리자 확인이 필요함을 메시지로 안내한다.
    GAMIFICATION_STATE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "게이미피케이션 상태를 확인할 수 없습니다. 관리자에게 문의해주세요."
    ),
    USER_INTEREST_CONCEPT_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 등록된 관심 개념입니다."
    ),
    CONCEPT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않거나 사용할 수 없는 개념이 포함되어 있습니다."
    ),
    XP_HISTORY_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 처리된 XP 이력입니다."
    ),

    // ===== 서비스 간 통신 =====
    CONTENT_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "개념 정보를 확인할 수 없어 잠시 후 다시 시도해주세요."
    ),
    FEIGN_CLIENT_ERROR(
            HttpStatus.BAD_GATEWAY,
            "서비스 간 통신 중 오류가 발생했습니다."
    ),
    INTERNAL_CALL_SIGNATURE_INVALID(
            HttpStatus.UNAUTHORIZED,
            "내부 호출 서명이 유효하지 않습니다."
    ),
    INTERNAL_CALL_TIMESTAMP_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "내부 호출 요청이 만료되었습니다(재전송 의심)."
    ),
    // HmacVerificationFilter는 "유효하게 서명된 내부 호출인가"만 확인하고 "어느 서비스가
    // 이 API를 호출할 수 있는가"는 확인하지 않는다 — 서명은 유효하지만 이 API의 허용
    // 대상이 아닌 서비스가 호출한 경우에 쓴다(PR #124 리뷰, 용현님).
    INTERNAL_CALLER_NOT_ALLOWED(HttpStatus.FORBIDDEN, "이 내부 API를 호출할 수 없는 서비스입니다.");

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
