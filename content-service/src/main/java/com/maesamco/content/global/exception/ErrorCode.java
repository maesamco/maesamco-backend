package com.maesamco.content.global.exception;

import lombok.Getter;
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
@Getter
public enum ErrorCode {

    // ===== common =====
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    // 엔티티에 종속되지 않은 JPA 낙관적 락 충돌에 사용합니다.
    RESOURCE_MODIFIED_CONCURRENTLY(
            HttpStatus.CONFLICT,
            "리소스가 다른 요청에 의해 수정되었습니다. 최신 정보를 조회한 후 다시 시도해주세요."
    ),

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
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "오늘 생성된 퀴즈 세트가 없습니다."),
    QUESTION_NOT_ASSIGNED(HttpStatus.NOT_FOUND, "배정되지 않은 문제입니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출된 문항입니다."),
    QUIZ_EXPIRED(HttpStatus.GONE, "만료된 퀴즈 세트입니다."),
    LATEST_VERSION_NOT_FLAGGED(HttpStatus.CONFLICT, "수정할 수 없는 상태입니다."),
    INVALID_QUIZ_STATUS(HttpStatus.CONFLICT, "제출할 수 없는 상태입니다."),

    /* Curriculum */
    CURRICULUM_NOT_FOUND(HttpStatus.NOT_FOUND, "커리큘럼을 찾을 수 없습니다."),

    /* Unit */
    UNIT_NOT_FOUND(HttpStatus.NOT_FOUND, "유닛을 찾을 수 없습니다."),
    // 순서 변경 요청의 displayOrder가 1..(같은 커리큘럼의 활성 유닛 수) 범위를 벗어난 경우 (#324)
    UNIT_DISPLAY_ORDER_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "유닛 표시 순서는 1 이상, 같은 커리큘럼의 유닛 수 이하여야 합니다."),

    /* Lesson */
    LESSON_NOT_FOUND(HttpStatus.NOT_FOUND, "레슨을 찾을 수 없습니다."),
    // 순서 변경 요청의 displayOrder가 1..(같은 유닛의 활성 레슨 수) 범위를 벗어난 경우 (#324)
    LESSON_DISPLAY_ORDER_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "레슨 표시 순서는 1 이상, 같은 유닛의 레슨 수 이하여야 합니다."),

    /* Problem */
    PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "문제를 찾을 수 없습니다."),
    // 문제 수정 요청의 클라이언트 lockVersion 불일치에 사용합니다.
    PROBLEM_MODIFIED_CONCURRENTLY(HttpStatus.CONFLICT, "문제가 다른 요청에 의해 수정되었습니다. 최신 정보를 조회한 후 다시 시도해주세요."),
    STARTER_CODE_NOT_INITIALIZED(HttpStatus.BAD_REQUEST, "problem.starterCode의 JsonNullable 객체가 초기화되어야 합니다."),
    INVALID_PROBLEM_TYPE(HttpStatus.BAD_REQUEST, "현재 문제 도메인은 CODE 유형만 지원합니다."),
    INVALID_PROBLEM_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "허용되지 않은 문제 상태 변경입니다."),
    PROBLEM_PUBLICATION_TEST_CASE_REQUIRED(HttpStatus.CONFLICT, "문제를 발행하려면 승인된 테스트케이스가 최소 1개 이상 필요합니다."),

    /* Tag */
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "태그를 찾을 수 없습니다."),
    TAG_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 태그 이름입니다."),

    /* Problem Tag */
    PROBLEM_TAG_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 문제에 등록된 태그입니다."),
    PROBLEM_TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "문제에 등록된 태그를 찾을 수 없습니다."),

    /* Testcase */
    TEST_CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "테스트케이스를 찾을 수 없습니다."),
    TEST_CASE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "비공개 테스트케이스에 접근할 권한이 없습니다."),

    /* Problem Version */
    PROBLEM_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 문제에 속한 문제 버전을 찾을 수 없습니다."),
    PROBLEM_VERSION_INVALID_VERSION_NO(HttpStatus.BAD_REQUEST, "문제 버전 번호는 1 이상이어야 합니다."),

    /* Problem Progress */
    PROBLEM_PROGRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "문제 풀이 진행 상태를 찾을 수 없습니다."),
    PROBLEM_PROGRESS_INVALID_USER_ID(HttpStatus.BAD_REQUEST, "사용자 식별자는 null일 수 없습니다."),
    PROBLEM_PROGRESS_INVALID_PROBLEM_ID(HttpStatus.BAD_REQUEST, "문제 식별자는 null일 수 없습니다."),
    PROBLEM_PROGRESS_INVALID_SUBMISSION_RESULT(HttpStatus.BAD_REQUEST, "지원하지 않는 채점 결과입니다."),
    PROBLEM_PROGRESS_INVALID_ATTEMPT_NO(HttpStatus.BAD_REQUEST, "제출 시도 번호는 1 이상이어야 합니다."),
    PROBLEM_PROGRESS_INVALID_STATUS(HttpStatus.BAD_REQUEST, "문제 풀이 상태가 올바르지 않습니다."),
    PROBLEM_PROGRESS_INVALID_JUDGED_AT(HttpStatus.BAD_REQUEST, "채점 완료 시각은 null일 수 없습니다."),

    // HmacVerificationFilter는 "유효하게 서명된 내부 호출인가"만 확인하고 "어느 서비스가
    // 이 API를 호출할 수 있는가"는 확인하지 않는다 — 서명은 유효하지만 이 API의 허용
    // 대상이 아닌 서비스가 호출한 경우에 쓴다(PR #124 리뷰, 용현님).
    INTERNAL_CALLER_NOT_ALLOWED(HttpStatus.FORBIDDEN, "이 내부 API를 호출할 수 없는 서비스입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
