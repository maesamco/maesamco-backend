package com.maesamco.content.application.dailyquiz.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Daily Quiz 완료 이벤트 발행에 필요한 의미 데이터
 *
 * Kafka DTO나 JSON 형식과 분리하여 Application 계층이 특정 메시징 구현에
 * 의존하지 않도록 합니다.
 */
public record DailyQuizCompletedEventData(
        Instant occurredAt,
        UUID quizAttemptId,
        UUID userId,
        int correctCount,
        int totalCount,
        Instant completedAt,
        List<QuestionResult> questionResults
) {

    public DailyQuizCompletedEventData {
        questionResults = List.copyOf(questionResults);
    }

    public record QuestionResult(
            UUID questionVersionId,
            List<String> conceptTags,
            boolean correct
    ) {

        public QuestionResult {
            conceptTags = List.copyOf(conceptTags);
        }
    }
}
