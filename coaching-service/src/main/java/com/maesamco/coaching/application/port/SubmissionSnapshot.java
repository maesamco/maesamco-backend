package com.maesamco.coaching.application.port;

import java.util.List;
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

    public boolean isWrong() {
        return "WRONG".equals(result);
    }

    public record FailedTest(boolean isPublic, String errorType) {
    }
}
