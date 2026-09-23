package com.maesamco.content.domain.entity;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemEventOutboxTest {

    private static final int MAX_RETRY_COUNT = 5;

    private static final String PAYLOAD = """
            {
              "eventType": "PROBLEM_PUBLISHED"
            }
            """;

    private static final String KAFKA_PUBLISH_FAILED =
            "KAFKA_PUBLISH_FAILED:IllegalStateException";

    private static final String POST_PUBLISH_FAILED =
            "KAFKA_PUBLISH_OUTCOME_UNKNOWN";

    private static final String PAYLOAD_TOO_LARGE =
            "EVENT_PAYLOAD_TOO_LARGE";

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-09-23T00:00:00Z");

    @Nested
    @DisplayName("PENDING Outbox 생성")
    class CreatePending {

        @Test
        @DisplayName("Problem Published Outbox 생성 시 이벤트 메타데이터를 초기화한다")
        void createPending_initializesEventMetadata() {
            // given
            UUID eventId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();

            // when
            ProblemEventOutbox outbox =
                    ProblemEventOutbox.createPending(
                            eventId,
                            problemId,
                            1,
                            PAYLOAD,
                            OCCURRED_AT
                    );

            // then
            assertThat(outbox.getEventId()).isEqualTo(eventId);
            assertThat(outbox.getAggregateType()).isEqualTo("PROBLEM");
            assertThat(outbox.getAggregateId()).isEqualTo(problemId);
            assertThat(outbox.getEventType()).isEqualTo("PROBLEM_PUBLISHED");
            assertThat(outbox.getEventVersion()).isEqualTo(1);
            assertThat(outbox.getPayload()).isEqualTo(PAYLOAD);
            assertThat(outbox.getOccurredAt()).isEqualTo(OCCURRED_AT);
        }

        @Test
        @DisplayName("새 Outbox는 PENDING 상태로 생성된다")
        void createPending_initializesPendingStatus() {
            // given & when
            ProblemEventOutbox outbox = createPendingOutbox();

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
        }

        @Test
        @DisplayName("새 Outbox의 retryCount는 0이다")
        void createPending_initializesRetryCountToZero() {
            // given & when
            ProblemEventOutbox outbox = createPendingOutbox();

            // then
            assertThat(outbox.getRetryCount()).isZero();
        }

        @Test
        @DisplayName("새 Outbox의 재시도 및 실패 관련 필드는 초기화되어 있다")
        void createPending_initializesRetryState() {
            // given & when
            ProblemEventOutbox outbox = createPendingOutbox();

            // then
            assertThat(outbox.getNextAttemptAt()).isNull();
            assertThat(outbox.getPublishedAt()).isNull();
            assertThat(outbox.getLastError()).isNull();
        }
    }

    @Nested
    @DisplayName("발행 완료")
    class MarkPublished {

        @Test
        @DisplayName("PENDING Outbox를 발행 완료 처리하면 PUBLISHED 상태가 된다")
        void markPublished_changesStatusToPublished() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();
            Instant publishedAt = Instant.parse("2026-09-23T00:10:00Z");

            // when
            outbox.markPublished(publishedAt);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
        }

        @Test
        @DisplayName("발행 완료 처리하면 publishedAt을 기록한다")
        void markPublished_recordsPublishedAt() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();
            Instant publishedAt = Instant.parse("2026-09-23T00:10:00Z");

            // when
            outbox.markPublished(publishedAt);

            // then
            assertThat(outbox.getPublishedAt()).isEqualTo(publishedAt);
        }
    }

    @Nested
    @DisplayName("발행 실패 및 Retry")
    class Retry {

        @Test
        @DisplayName("발행 실패를 기록하면 retryCount가 증가한다")
        void recordFailure_incrementsRetryCount() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.recordFailure(KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("발행 실패 사유를 lastError에 기록한다")
        void recordFailure_recordsLastError() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.recordFailure(KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

            // then
            assertThat(outbox.getLastError()).isEqualTo(KAFKA_PUBLISH_FAILED);
        }

        @Test
        @DisplayName("최대 재시도 전의 실패는 PENDING 상태를 유지한다")
        void recordFailure_beforeMaxRetry_keepsPending() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.recordFailure(KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("재시도 가능한 실패이면 nextAttemptAt을 설정한다")
        void recordFailure_setsNextAttemptAt() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.recordFailure(KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

            // then
            assertThat(outbox.getNextAttemptAt()).isNotNull();
        }

        @Test
        @DisplayName("최대 재시도 횟수에 도달하면 FAILED 상태로 전환한다")
        void recordFailure_reachesMaxRetry_marksFailed() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();
            ReflectionTestUtils.setField(outbox, "retryCount", MAX_RETRY_COUNT - 1);

            // when
            outbox.recordFailure(KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(MAX_RETRY_COUNT);
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.FAILED);
            assertThat(outbox.getLastError()).isEqualTo(KAFKA_PUBLISH_FAILED);
        }
    }

    @Nested
    @DisplayName("Post Publish 실패")
    class PostPublishFailure {

        @Test
        @DisplayName("Post Publish 실패를 기록해도 PENDING 상태를 유지한다")
        void recordPostPublishFailure_keepsPendingStatus() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.recordPostPublishFailure(POST_PUBLISH_FAILED);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
            assertThat(outbox.getPublishedAt()).isNull();
        }

        @Test
        @DisplayName("Post Publish 실패를 기록하면 retryCount가 증가한다")
        void recordPostPublishFailure_incrementsRetryCount() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.recordPostPublishFailure(POST_PUBLISH_FAILED);

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Post Publish 실패 사유를 lastError에 기록한다")
        void recordPostPublishFailure_recordsLastError() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.recordPostPublishFailure(POST_PUBLISH_FAILED);

            // then
            assertThat(outbox.getLastError()).isEqualTo(POST_PUBLISH_FAILED);
        }
    }

    @Nested
    @DisplayName("강제 FAILED 처리")
    class MarkFailed {

        @Test
        @DisplayName("복구 불가능한 오류이면 FAILED 상태로 변경한다")
        void markFailed_changesStatusToFailed() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.markFailed(PAYLOAD_TOO_LARGE);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.FAILED);
        }

        @Test
        @DisplayName("강제 FAILED 처리하면 실패 사유를 기록한다")
        void markFailed_recordsLastError() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.markFailed(PAYLOAD_TOO_LARGE);

            // then
            assertThat(outbox.getLastError()).isEqualTo(PAYLOAD_TOO_LARGE);
        }

        @Test
        @DisplayName("강제 FAILED 처리 시 publishedAt은 기록하지 않는다")
        void markFailed_doesNotSetPublishedAt() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.markFailed(PAYLOAD_TOO_LARGE);

            // then
            assertThat(outbox.getPublishedAt()).isNull();
        }
    }

    private ProblemEventOutbox createPendingOutbox() {
        return ProblemEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                PAYLOAD,
                OCCURRED_AT
        );
    }
}