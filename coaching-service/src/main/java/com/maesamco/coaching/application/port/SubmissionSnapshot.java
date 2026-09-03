package com.maesamco.coaching.application.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Judge Service GET /internal/v1/submissions/{submissionId} 조회 결과를
 * 도메인 계층에서 쓰기 좋은 형태로 옮겨온 값. Feign 원본 응답 DTO
 * (infrastructure/feign/SubmissionDetailResponse)와 필드가 겹치지만,
 * 이 계층은 Judge Service의 JSON 응답 구조가 나중에 바뀌어도 영향받지 않는다
 * (JudgeServiceAdapter가 변환을 흡수).
 */
public record SubmissionSnapshot(
        UUID submissionId,
        UUID userId,
        UUID problemId,
        String code,
        String result,
        List<FailedTest> failedTestSummary,
        int attemptNo
) {

    /**
     * PR #70 리뷰 교차검증 — Judge Service의 result는 CORRECT/WRONG 둘뿐이 아니라
     * COMPILE_ERROR/RUNTIME_ERROR/TIME_LIMIT_EXCEEDED/MEMORY_LIMIT_EXCEEDED도 있다
     * (매삼코_DB_테이블_명세, judge-service의 SubmissionResult enum). "WRONG"만 확인하면
     * 컴파일 오류·런타임 오류 등으로 실패한 제출은 힌트를 요청할 수 없게(HINT_NOT_ALLOWED)
     * 잘못 막혀버린다 — 채점이 아직 안 끝나 result가 null인 경우도 안전하게 false로 처리.
     */
    private static final Set<String> HINT_ELIGIBLE_RESULTS = Set.of(
            "WRONG", "COMPILE_ERROR", "RUNTIME_ERROR", "TIME_LIMIT_EXCEEDED", "MEMORY_LIMIT_EXCEEDED"
    );

    public boolean isIncorrect() {
        // Set.of()로 만든 불변 집합은 contains(null)에서 NPE를 던진다(null 원소 자체를
        // 금지하는 구현) — result == null(아직 채점 중)을 먼저 걸러야 한다.
        return result != null && HINT_ELIGIBLE_RESULTS.contains(result);
    }

    public record FailedTest(boolean isPublic, String errorType) {
    }
}
