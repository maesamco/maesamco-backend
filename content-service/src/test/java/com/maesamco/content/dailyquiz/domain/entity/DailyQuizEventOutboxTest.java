package com.maesamco.content.dailyquiz.domain.entity;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutboxStatus;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Daily Quiz 완료 이벤트 Outbox의 생성 규칙을 검증합니다.
 */
class DailyQuizEventOutboxTest {

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-09-16T00:00:00Z");

    private static final Instant NEXT_ATTEMPT_AT =
            Instant.parse("2026-09-16T00:01:00Z");

    private static final Instant PUBLISHED_AT =
            Instant.parse("2026-09-16T00:02:00Z");

    @Test
    @DisplayName("DailyQuizCompleted Outbox를 생성하면 PENDING 상태로 초기화된다")
    void createPending_initializesPendingOutbox() {
        UUID eventId = UUID.randomUUID();
        UUID quizAttemptId = UUID.randomUUID();
        String payload = "{\"eventType\":\"DAILY_QUIZ_COMPLETED\"}";

        DailyQuizEventOutbox outbox =
                DailyQuizEventOutbox.createPending(
                        eventId,
                        quizAttemptId,
                        "DAILY_QUIZ_COMPLETED",
                        1,
                        payload,
                        OCCURRED_AT
                );

        assertThat(outbox.getEventId()).isEqualTo(eventId);
        assertThat(outbox.getAggregateType()).isEqualTo("DAILY_QUIZ");
        assertThat(outbox.getAggregateId()).isEqualTo(quizAttemptId);
        assertThat(outbox.getEventType()).isEqualTo("DAILY_QUIZ_COMPLETED");
        assertThat(outbox.getEventVersion()).isEqualTo(1);
        assertThat(outbox.getPayload()).isEqualTo(payload);
        assertThat(outbox.getStatus()).isEqualTo(DailyQuizEventOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getVersion()).isZero();
        assertThat(outbox.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(outbox.getPublishedAt()).isNull();
        assertThat(outbox.getLastError()).isNull();
    }

    @Test
    @DisplayName("이벤트 ID가 없으면 Outbox를 생성할 수 없다")
    void createPending_rejectsNullEventId() {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                null,
                UUID.randomUUID(),
                "DAILY_QUIZ_COMPLETED",
                1,
                "{}",
                OCCURRED_AT
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("이벤트 ID는 필수입니다.");
    }

    @Test
    @DisplayName("퀴즈 세트 ID가 없으면 Outbox를 생성할 수 없다")
    void createPending_rejectsNullQuizAttemptId() {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                null,
                "DAILY_QUIZ_COMPLETED",
                1,
                "{}",
                OCCURRED_AT
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("퀴즈 세트 ID는 필수입니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("이벤트 버전이 1 미만이면 Outbox를 생성할 수 없다")
    void createPending_rejectsInvalidEventVersion(int eventVersion) {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DAILY_QUIZ_COMPLETED",
                eventVersion,
                "{}",
                OCCURRED_AT
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("이벤트 버전은 1 이상이어야 합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    @DisplayName("이벤트 본문이 비어 있으면 Outbox를 생성할 수 없다")
    void createPending_rejectsBlankPayload(String payload) {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DAILY_QUIZ_COMPLETED",
                1,
                payload,
                OCCURRED_AT
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("이벤트 본문은 필수입니다.");
    }

    @Test
    @DisplayName("이벤트 발생 시각이 없으면 Outbox를 생성할 수 없다")
    void createPending_rejectsNullOccurredAt() {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DAILY_QUIZ_COMPLETED",
                1,
                "{}",
                null
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("이벤트 발생 시각은 필수입니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    @DisplayName("이벤트 타입이 비어 있으면 Outbox를 생성할 수 없다")
    void createPending_rejectsBlankEventType(String eventType) {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventType,
                1,
                "{}",
                OCCURRED_AT
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("이벤트 타입은 필수입니다.");
    }

    @Test
    @DisplayName("Kafka 발행에 성공하면 PUBLISHED 상태와 발행 시각을 기록한다")
    void recordPublishSuccess_recordsPublicationResult() {
        DailyQuizEventOutbox outbox = createPendingOutbox();

        outbox.recordPublishSuccess(PUBLISHED_AT);

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PUBLISHED);
        assertThat(outbox.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getLastError()).isNull();
    }

    @Test
    @DisplayName("재시도 한도 미만의 발행 실패는 PENDING 상태와 다음 시도 시각을 유지한다")
    void recordPublishFailure_schedulesNextAttemptBeforeRetryLimit() {
        DailyQuizEventOutbox outbox = createPendingOutbox();

        outbox.recordPublishFailure(
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                NEXT_ATTEMPT_AT
        );

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NEXT_ATTEMPT_AT);
        assertThat(outbox.getPublishedAt()).isNull();
        assertThat(outbox.getLastError()).isEqualTo("KAFKA_PUBLISH_TIMEOUT");
    }

    @Test
    @DisplayName("발행 실패 횟수가 재시도 한도에 도달하면 FAILED 상태가 된다")
    void recordPublishFailure_marksFailedAtRetryLimit() {
        DailyQuizEventOutbox outbox = createPendingOutbox();

        outbox.recordPublishFailure(
                "KAFKA_PUBLISH_TIMEOUT",
                2,
                NEXT_ATTEMPT_AT
        );
        outbox.recordPublishFailure(
                "KAFKA_PUBLISH_TIMEOUT",
                2,
                NEXT_ATTEMPT_AT.plusSeconds(60)
        );

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(2);
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getPublishedAt()).isNull();
        assertThat(outbox.getLastError()).isEqualTo("KAFKA_PUBLISH_TIMEOUT");
    }

    @Test
    @DisplayName("재시도 후 발행에 성공하면 실패 정보를 제거하고 실패 횟수는 유지한다")
    void recordPublishSuccess_clearsRetryScheduleAndError() {
        DailyQuizEventOutbox outbox = createPendingOutbox();
        outbox.recordPublishFailure(
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                NEXT_ATTEMPT_AT
        );

        outbox.recordPublishSuccess(PUBLISHED_AT);

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PUBLISHED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(outbox.getLastError()).isNull();
    }

    @Test
    @DisplayName("복구할 수 없는 발행 실패는 즉시 FAILED 상태로 변경한다")
    void recordUnrecoverablePublishFailure_recordsTerminalFailure() {
        DailyQuizEventOutbox outbox = createPendingOutbox();

        outbox.recordUnrecoverablePublishFailure("EVENT_PAYLOAD_TOO_LARGE");

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getPublishedAt()).isNull();
        assertThat(outbox.getLastError()).isEqualTo("EVENT_PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("발행 완료 시각이 없으면 발행 성공을 기록할 수 없다")
    void recordPublishSuccess_rejectsNullPublishedAt() {
        DailyQuizEventOutbox outbox = createPendingOutbox();

        assertThatThrownBy(() -> outbox.recordPublishSuccess(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("발행 완료 시각은 필수입니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("최대 재시도 횟수가 1 미만이면 실패를 기록할 수 없다")
    void recordPublishFailure_rejectsInvalidMaxRetryCount(int maxRetryCount) {
        DailyQuizEventOutbox outbox = createPendingOutbox();

        assertThatThrownBy(() -> outbox.recordPublishFailure(
                "KAFKA_PUBLISH_TIMEOUT",
                maxRetryCount,
                NEXT_ATTEMPT_AT
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("최대 재시도 횟수는 1 이상이어야 합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    @DisplayName("실패 사유가 비어 있으면 발행 실패를 기록할 수 없다")
    void recordPublishFailure_rejectsBlankError(String error) {
        DailyQuizEventOutbox outbox = createPendingOutbox();

        assertThatThrownBy(() -> outbox.recordPublishFailure(
                error,
                3,
                NEXT_ATTEMPT_AT
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("발행 실패 사유는 필수입니다.");
    }

    @Test
    @DisplayName("다음 발행 시도 시각이 없으면 실패를 기록할 수 없다")
    void recordPublishFailure_rejectsNullNextAttemptAt() {
        DailyQuizEventOutbox outbox = createPendingOutbox();

        assertThatThrownBy(() -> outbox.recordPublishFailure(
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                null
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("다음 발행 시도 시각은 필수입니다.");
    }

    @Test
    @DisplayName("PUBLISHED 상태의 Outbox는 다시 변경할 수 없다")
    void publishedOutbox_rejectsAdditionalTransition() {
        DailyQuizEventOutbox outbox = createPendingOutbox();
        outbox.recordPublishSuccess(PUBLISHED_AT);

        assertThatThrownBy(() -> outbox.recordUnrecoverablePublishFailure(
                "KAFKA_PUBLISH_FAILED"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PENDING 상태의 Outbox만 변경할 수 있습니다.");
    }

    @Test
    @DisplayName("FAILED 상태의 Outbox는 다시 변경할 수 없다")
    void failedOutbox_rejectsAdditionalTransition() {
        DailyQuizEventOutbox outbox = createPendingOutbox();
        outbox.recordUnrecoverablePublishFailure("EVENT_PAYLOAD_TOO_LARGE");

        assertThatThrownBy(() -> outbox.recordPublishSuccess(PUBLISHED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PENDING 상태의 Outbox만 변경할 수 있습니다.");
    }

    private DailyQuizEventOutbox createPendingOutbox() {
        return DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DAILY_QUIZ_COMPLETED",
                1,
                "{\"eventType\":\"DAILY_QUIZ_COMPLETED\"}",
                OCCURRED_AT
        );
    }
}
