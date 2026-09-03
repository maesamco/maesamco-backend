package com.maesamco.coaching.infrastructure.feign;

import java.util.List;
import java.util.UUID;

/**
 * Judge Service GET /internal/v1/submissions/{submissionId} 응답 바디(data 부분)를
 * 그대로 옮긴 DTO. Notion API 명세서(5. API 명세서 → Judge Service → "제출 상세 조회",
 * 2026-09-02 확인) 기준이며, 아직 팀이 최종 확정한 계약은 아니라 필드가 바뀔 수 있다 —
 * 바뀌면 이 DTO와 JudgeServiceAdapter만 고치면 된다.
 */
public record SubmissionDetailResponse(
        UUID submissionId,
        UUID userId,
        UUID problemId,
        String code,
        String result,
        List<FailedTestSummary> failedTestSummary,
        int attemptNo
) {

    public record FailedTestSummary(boolean isPublic, String errorType) {
    }
}
