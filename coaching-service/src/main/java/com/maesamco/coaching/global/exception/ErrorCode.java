package com.maesamco.coaching.global.exception;

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
    INTERNAL_CALL_TIMESTAMP_EXPIRED(HttpStatus.UNAUTHORIZED, "내부 호출 요청이 만료되었습니다(재전송 의심)."),

    // ===== coaching =====
    COACHING_SESSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료된 코칭 세션입니다."),
    // PR #70 리뷰 — 이 코드는 UNIQUE(submission_id) 위반과 V4의 (user_id, problem_id)
    // WHERE IN_PROGRESS 파셜 인덱스 위반 둘 다에서 재사용된다(CoachingSessionRepositoryImpl.
    // save()가 어떤 제약이 위반됐는지 구분하지 않음) — 메시지를 두 경우 모두에 맞게 일반화했다.
    COACHING_SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 조건에 맞는 코칭 세션이 존재합니다."),
    // 이슈 #51 — FollowUpAnswerPersistenceService가 팀 컨벤션 406행(엔티티 전달 규칙)에
    // 따라 세션을 ID로 다시 조회할 때의 방어용. Facade가 이미 한 번 조회해 존재를
    // 확인한 뒤라 실제로는 거의 발생하지 않는다(세션 삭제 기능 자체가 없음).
    COACHING_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "코칭 세션을 찾을 수 없습니다."),
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "제출을 찾을 수 없습니다."),
    HINT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "본인 제출이 오답 상태일 때만 힌트를 요청할 수 있습니다."),
    HINT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 해당 단계의 힌트가 존재합니다."),
    EXPLANATION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "본인 제출이 정답 상태일 때만 설명을 등록할 수 있습니다."),
    EXPLANATION_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 설명을 찾을 수 없습니다."),
    // 이슈 #84 결정 2(2026-09-03) — 유일성 기준이 코칭 세션이 아니라 제출(submission_id)
    // 단위로 바뀌어서 메시지도 다시 제출 기준으로 되돌렸다. 같은 문제를 다른 접근으로
    // 재도전해서 새로 정답 제출하면, 그 새 제출에 대해서는 이 예외가 나지 않는다.
    EXPLANATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 설명이 등록된 제출입니다."),
    FOLLOW_UP_QUESTION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 해당 설명에 대한 역질문이 존재합니다."),
    FOLLOW_UP_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "역질문을 찾을 수 없습니다."),
    FOLLOW_UP_ANSWER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 해당 역질문에 대한 답변이 존재합니다."),
    AI_FEEDBACK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 해당 코칭 세션에 대한 AI 피드백이 존재합니다."),
    AI_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "힌트 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    WEAK_CONCEPT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 해당 사용자·개념에 대한 취약 개념 집계 행이 존재합니다.");

    // 이 아래에 서비스별 섹션을 추가하세요. 예)
    // ===== user =====
    // USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    //
    // ===== judge =====
    // SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "제출을 찾을 수 없습니다."),
    // IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "동일한 키로 다른 요청이 이미 처리되었습니다.");

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
