package com.maesamco.user.infrastructure.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Content Service가 발행하는 DailyQuizCompleted 이벤트의 역직렬화 DTO입니다.
 *
 * <p>생산자 계약(eventVersion 1)에는 개념 태그·문항별 결과({@code conceptTags},
 * {@code questionResults}) 등 User Service가 쓰지 않는 필드도 포함됩니다.
 * 보상 처리에 필요한 필드만 받고, 생산자가 필드를 추가해도 소비가 깨지지 않도록
 * 알 수 없는 필드는 무시합니다.</p>
 *
 * <p>Content Service의 Outbox는 발행 결과가 불확실하면 같은 퀴즈 시도를 다시
 * 발행할 수 있습니다. "퀴즈 1회 = 보상 1회"라는 업무 의미에 맞춰
 * {@code quizAttemptId}(Kafka 메시지 Key와 동일)를 원천 이벤트 식별자로 사용합니다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyQuizCompletedEvent(
        UUID eventId,
        UUID quizAttemptId,
        UUID userId,
        int correctCount,
        int totalCount,
        Instant completedAt
) {

    public DailyQuizCompletedEvent {
        Objects.requireNonNull(quizAttemptId, "quizAttemptId는 필수입니다.");
        Objects.requireNonNull(userId, "userId는 필수입니다.");
        Objects.requireNonNull(completedAt, "completedAt은 필수입니다.");
    }
}
