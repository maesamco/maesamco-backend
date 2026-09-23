package com.maesamco.content.infrastructure.messaging.event;

import com.maesamco.content.application.command.ProblemProgressSyncCommand;

import java.time.Instant;
import java.util.UUID;

/**
 * Judge Service가 발행한 SubmissionJudged Kafka 이벤트를 나타냅니다.
 *
 * @param submissionId     채점이 완료된 제출 식별자
 * @param userId           제출한 사용자 식별자
 * @param problemId        제출 대상 문제 식별자
 * @param problemVersionId 채점에 사용된 문제 버전 식별자
 * @param attemptNo        해당 문제에 대한 사용자의 제출 시도 번
 * @param status           Submission 상태
 * @param result           채점 결과
 * @param judgedAt         채점이 완료된 시각
 */
public record SubmissionJudgedEvent(
        UUID submissionId,
        UUID userId,
        UUID problemId,
        UUID problemVersionId,
        int attemptNo,
        String status,
        String result,
        Instant judgedAt
) {

    /**
     * Kafka 이벤트를 ProblemProgress 처리용 Command로 변환합니다.
     */
    public ProblemProgressSyncCommand toCommand() {
        return new ProblemProgressSyncCommand(
                submissionId,
                userId,
                problemId,
                problemVersionId,
                attemptNo,
                status,
                result,
                judgedAt
        );
    }
}