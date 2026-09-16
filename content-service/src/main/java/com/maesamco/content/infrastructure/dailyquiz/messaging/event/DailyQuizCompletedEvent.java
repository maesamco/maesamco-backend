package com.maesamco.content.infrastructure.dailyquiz.messaging.event;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttempt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 사용자가 Daily Quiz의 모든 문항을 제출했음을
 * User Service와 Coaching Service에 전달하는 Kafka 이벤트
 *
 * 마지막 문항 제출 트랜잭션에서 Outbox payload로 저장되며,
 * 후속 Relay가 저장된 JSON을 Kafka로 발행
 */
public record DailyQuizCompletedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID quizAttemptId,
        UUID userId,
        List<String> conceptTags,
        int correctCount,
        int totalCount,
        Instant completedAt,
        List<QuestionResult> questionResults
) {

    public static final String EVENT_TYPE = "DAILY_QUIZ_COMPLETED";

    public static final int EVENT_VERSION = 1;

    /**
     * 외부에서 전달받은 목록이 이벤트 생성 이후 변경되지 않도록
     * 불변 복사본으로 보관
     */
    public DailyQuizCompletedEvent {
        conceptTags = List.copyOf(conceptTags);
        questionResults = List.copyOf(questionResults);
    }

    /**
     * 완료된 Daily Quiz 세트와 문항별 결과를 기반으로 이벤트를 생성
     */
    public static DailyQuizCompletedEvent fromCompletedAttempt(
            UUID eventId,
            Instant occurredAt,
            DailyQuizAttempt attempt,
            List<QuestionResult> questionResults
    ) {
        List<String> conceptTags =
                questionResults.stream()
                        .flatMap(result -> result.conceptTags().stream())
                        .distinct()
                        .toList();

        return new DailyQuizCompletedEvent(
                eventId,
                EVENT_TYPE,
                EVENT_VERSION,
                occurredAt,
                attempt.getId(),
                attempt.getUserId(),
                conceptTags,
                attempt.getCorrectCount(),
                attempt.getTotalCount(),
                attempt.getCompletedAt(),
                questionResults
        );
    }

    /**
     * Daily Quiz 한 문항의 채점 결과
     */
    public record QuestionResult(
            // 어떤 문제 버전의 결과인지 식별하기 위한 값
            UUID questionVersionId,
            List<String> conceptTags,
            boolean correct
    ) {
        /**
         * 문항의 개념 목록이 결과 생성 이후 변경되지 않도록
         * 불변 복사본으로 보관
         */
        public QuestionResult {
            conceptTags = List.copyOf(conceptTags);
        }
    }
}
