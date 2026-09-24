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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static final UUID CLAIM_ID = UUID.randomUUID();

    private static final Instant CLAIMED_AT =
            Instant.parse("2026-09-23T00:05:00Z");

    private static final Instant LEASE_UNTIL =
            Instant.parse("2026-09-23T00:06:00Z");

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
    @DisplayName("발행 선점 (#160)")
    class Claim {

        @Test
        @DisplayName("PENDING Outbox를 선점하면 IN_PROGRESS 상태가 되고 claimId와 leaseUntil을 기록한다")
        void claim_pendingOutbox_marksInProgress() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when
            outbox.claim(CLAIM_ID, CLAIMED_AT, LEASE_UNTIL);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.IN_PROGRESS);
            assertThat(outbox.getClaimId()).isEqualTo(CLAIM_ID);
            assertThat(outbox.getLeaseUntil()).isEqualTo(LEASE_UNTIL);
            assertThat(outbox.isClaimedBy(CLAIM_ID)).isTrue();
        }

        @Test
        @DisplayName("재시도 시각이 도래하지 않은 PENDING Outbox는 선점할 수 없다")
        void claim_futureNextAttemptAt_throws() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();
            ReflectionTestUtils.setField(outbox, "nextAttemptAt", CLAIMED_AT.plusSeconds(1));

            // when & then
            assertThatThrownBy(() -> outbox.claim(CLAIM_ID, CLAIMED_AT, LEASE_UNTIL))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("lease가 남아있는 IN_PROGRESS Outbox는 다른 Worker가 선점할 수 없다")
        void claim_activeLease_throws() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when & then
            assertThatThrownBy(() -> outbox.claim(UUID.randomUUID(), LEASE_UNTIL.minusSeconds(1), LEASE_UNTIL.plusSeconds(60)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("lease가 만료된 IN_PROGRESS Outbox는 새 Worker가 재선점하고 이전 claimId는 무효가 된다")
        void claim_expiredLease_reclaims() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();
            UUID newClaimId = UUID.randomUUID();

            // when
            outbox.claim(newClaimId, LEASE_UNTIL, LEASE_UNTIL.plusSeconds(60));

            // then
            assertThat(outbox.isClaimedBy(newClaimId)).isTrue();
            assertThat(outbox.isClaimedBy(CLAIM_ID)).isFalse();
        }

        @Test
        @DisplayName("leaseUntil이 선점 시각 이후가 아니면 선점할 수 없다")
        void claim_invalidLease_throws() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when & then
            assertThatThrownBy(() -> outbox.claim(CLAIM_ID, CLAIMED_AT, CLAIMED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("선점하지 않은 PENDING Outbox에는 발행 결과를 기록할 수 없다")
        void markPublished_withoutClaim_throws() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            // when & then
            assertThatThrownBy(() -> outbox.markPublished(CLAIM_ID, CLAIMED_AT))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("재선점된 뒤 이전 Worker의 늦은 결과 기록은 거부된다 (fencing)")
        void markPublished_staleClaim_throws() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();
            outbox.claim(UUID.randomUUID(), LEASE_UNTIL, LEASE_UNTIL.plusSeconds(60));

            // when & then
            assertThatThrownBy(() -> outbox.markPublished(CLAIM_ID, LEASE_UNTIL))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("발행 결과를 기록하면 선점 정보가 해제된다")
        void resultRecorded_clearsClaim() {
            // given
            ProblemEventOutbox published = createClaimedOutbox();
            ProblemEventOutbox retrying = createClaimedOutbox();

            // when
            published.markPublished(CLAIM_ID, CLAIMED_AT);
            retrying.recordFailure(CLAIM_ID, KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

            // then
            assertThat(published.getClaimId()).isNull();
            assertThat(published.getLeaseUntil()).isNull();
            assertThat(retrying.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
            assertThat(retrying.getClaimId()).isNull();
            assertThat(retrying.getLeaseUntil()).isNull();
        }
    }

    @Nested
    @DisplayName("발행 완료")
    class MarkPublished {

        @Test
        @DisplayName("선점한 Outbox를 발행 완료 처리하면 PUBLISHED 상태가 된다")
        void markPublished_changesStatusToPublished() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();
            Instant publishedAt = Instant.parse("2026-09-23T00:10:00Z");

            // when
            outbox.markPublished(CLAIM_ID, publishedAt);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
        }

        @Test
        @DisplayName("발행 완료 처리하면 publishedAt을 기록한다")
        void markPublished_recordsPublishedAt() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();
            Instant publishedAt = Instant.parse("2026-09-23T00:10:00Z");

            // when
            outbox.markPublished(CLAIM_ID, publishedAt);

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
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when
            outbox.recordFailure(CLAIM_ID, KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("발행 실패 사유를 lastError에 기록한다")
        void recordFailure_recordsLastError() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when
            outbox.recordFailure(CLAIM_ID, KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

            // then
            assertThat(outbox.getLastError()).isEqualTo(KAFKA_PUBLISH_FAILED);
        }

        @Test
        @DisplayName("최대 재시도 전의 실패는 PENDING 상태로 되돌린다")
        void recordFailure_beforeMaxRetry_keepsPending() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when
            outbox.recordFailure(CLAIM_ID, KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("재시도 가능한 실패이면 nextAttemptAt을 설정한다")
        void recordFailure_setsNextAttemptAt() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when
            outbox.recordFailure(CLAIM_ID, KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

            // then
            assertThat(outbox.getNextAttemptAt()).isNotNull();
        }

        @Test
        @DisplayName("최대 재시도 횟수에 도달하면 FAILED 상태로 전환한다")
        void recordFailure_reachesMaxRetry_marksFailed() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();
            ReflectionTestUtils.setField(outbox, "retryCount", MAX_RETRY_COUNT - 1);

            // when
            outbox.recordFailure(CLAIM_ID, KAFKA_PUBLISH_FAILED, MAX_RETRY_COUNT);

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
        @DisplayName("Post Publish 실패를 기록하면 PENDING 상태로 되돌린다")
        void recordPostPublishFailure_keepsPendingStatus() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when
            outbox.recordPostPublishFailure(CLAIM_ID, POST_PUBLISH_FAILED);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
            assertThat(outbox.getPublishedAt()).isNull();
        }

        @Test
        @DisplayName("Post Publish 실패를 기록하면 retryCount가 증가한다")
        void recordPostPublishFailure_incrementsRetryCount() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when
            outbox.recordPostPublishFailure(CLAIM_ID, POST_PUBLISH_FAILED);

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Post Publish 실패 사유를 lastError에 기록한다")
        void recordPostPublishFailure_recordsLastError() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when
            outbox.recordPostPublishFailure(CLAIM_ID, POST_PUBLISH_FAILED);

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
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when
            outbox.markFailed(CLAIM_ID, PAYLOAD_TOO_LARGE);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.FAILED);
        }

        @Test
        @DisplayName("강제 FAILED 처리하면 실패 사유를 기록한다")
        void markFailed_recordsLastError() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when
            outbox.markFailed(CLAIM_ID, PAYLOAD_TOO_LARGE);

            // then
            assertThat(outbox.getLastError()).isEqualTo(PAYLOAD_TOO_LARGE);
        }

        @Test
        @DisplayName("강제 FAILED 처리 시 publishedAt은 기록하지 않는다")
        void markFailed_doesNotSetPublishedAt() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            // when
            outbox.markFailed(CLAIM_ID, PAYLOAD_TOO_LARGE);

            // then
            assertThat(outbox.getPublishedAt()).isNull();
        }
    }

    private ProblemEventOutbox createClaimedOutbox() {
        ProblemEventOutbox outbox = createPendingOutbox();
        outbox.claim(CLAIM_ID, CLAIMED_AT, LEASE_UNTIL);
        return outbox;
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