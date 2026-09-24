package com.maesamco.content.application.persistence_service;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemEventOutboxPersistenceServiceTest {

    private static final int MAX_RETRY_COUNT = 5;

    private static final long FIRST_RETRY_DELAY_SECONDS = 60L;
    private static final long SECOND_RETRY_DELAY_SECONDS = 120L;
    private static final long THIRD_RETRY_DELAY_SECONDS = 240L;
    private static final long FOURTH_RETRY_DELAY_SECONDS = 480L;

    private static final String KAFKA_PUBLISH_FAILED = "KAFKA_PUBLISH_FAILED:IllegalStateException";
    private static final String POST_PUBLISH_FAILED = "KAFKA_PUBLISH_OUTCOME_UNKNOWN";
    private static final String PAYLOAD_TOO_LARGE = "EVENT_PAYLOAD_TOO_LARGE";

    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final Duration LEASE_DURATION = Duration.ofSeconds(60);

    @Mock
    private ProblemEventOutboxRepository problemEventOutboxRepository;

    @InjectMocks
    private ProblemEventOutboxPersistenceService problemEventOutboxPersistenceService;

    @Nested
    @DisplayName("발행 선점 (#160)")
    class ClaimNext {

        @Test
        @DisplayName("선점 가능한 Outbox가 있으면 IN_PROGRESS로 선점하고 저장한다")
        void claimNext_claimsCandidate() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();
            UUID claimId = UUID.randomUUID();

            when(problemEventOutboxRepository.findClaimableForUpdate(any(Instant.class), eq(1)))
                    .thenReturn(List.of(outbox));

            Instant before = Instant.now();

            // when
            Optional<ProblemEventOutbox> claimed =
                    problemEventOutboxPersistenceService.claimNext(claimId, LEASE_DURATION);

            Instant after = Instant.now();

            // then
            assertThat(claimed).containsSame(outbox);
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.IN_PROGRESS);
            assertThat(outbox.getClaimId()).isEqualTo(claimId);
            assertThat(outbox.getLeaseUntil())
                    .isAfterOrEqualTo(before.plus(LEASE_DURATION))
                    .isBeforeOrEqualTo(after.plus(LEASE_DURATION));

            verify(problemEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("선점 가능한 Outbox가 없으면 빈 결과를 반환하고 저장하지 않는다")
        void claimNext_noCandidate_returnsEmpty() {
            // given
            when(problemEventOutboxRepository.findClaimableForUpdate(any(Instant.class), eq(1)))
                    .thenReturn(List.of());

            // when
            Optional<ProblemEventOutbox> claimed =
                    problemEventOutboxPersistenceService.claimNext(UUID.randomUUID(), LEASE_DURATION);

            // then
            assertThat(claimed).isEmpty();
            verify(problemEventOutboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("lease가 만료된 IN_PROGRESS Outbox는 새 claimId로 재선점한다")
        void claimNext_expiredLease_reclaims() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();
            Instant past = Instant.now().minusSeconds(120);
            outbox.claim(CLAIM_ID, past, past.plusSeconds(60));
            UUID newClaimId = UUID.randomUUID();

            when(problemEventOutboxRepository.findClaimableForUpdate(any(Instant.class), eq(1)))
                    .thenReturn(List.of(outbox));

            // when
            problemEventOutboxPersistenceService.claimNext(newClaimId, LEASE_DURATION);

            // then
            assertThat(outbox.isClaimedBy(newClaimId)).isTrue();
            assertThat(outbox.isClaimedBy(CLAIM_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("선점 가드")
    class ClaimGuard {

        @Test
        @DisplayName("선점을 보유한 Outbox는 실패 기록 대상으로 처리되어 저장된다")
        void recordFailedAttempt_claimedOutbox_processesSuccessfully() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);

            verify(problemEventOutboxRepository).findById(outbox.getId());
            verify(problemEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("선점되지 않은 PENDING Outbox에는 결과를 기록하지 않는다")
        void markPublished_unclaimedOutbox_isIgnored() {
            // given
            ProblemEventOutbox outbox = createPendingOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            boolean updated = problemEventOutboxPersistenceService.markPublished(outbox.getId(), CLAIM_ID);

            // then
            assertThat(updated).isFalse();
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);

            verify(problemEventOutboxRepository, never()).save(outbox);
        }

        @Test
        @DisplayName("lease 만료 후 다른 Worker가 재선점했다면 이전 Worker의 결과 기록은 무시한다 (fencing)")
        void markPublished_staleClaimId_isIgnored() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();
            UUID newClaimId = UUID.randomUUID();
            Instant expiredAt = outbox.getLeaseUntil();

            outbox.claim(newClaimId, expiredAt, expiredAt.plusSeconds(60));

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            boolean updated = problemEventOutboxPersistenceService.markPublished(outbox.getId(), CLAIM_ID);

            // then
            assertThat(updated).isFalse();
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.IN_PROGRESS);
            assertThat(outbox.getClaimId()).isEqualTo(newClaimId);

            verify(problemEventOutboxRepository, never()).save(outbox);
        }

        @Test
        @DisplayName("이미 PUBLISHED 처리된 Outbox는 다시 실패 처리하지 않는다")
        void recordFailedAttempt_publishedOutbox_isIgnored() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();
            Instant publishedAt = Instant.parse("2026-09-23T00:10:00Z");

            outbox.markPublished(CLAIM_ID, publishedAt);

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
            assertThat(outbox.getPublishedAt()).isEqualTo(publishedAt);
            assertThat(outbox.getRetryCount()).isZero();

            verify(problemEventOutboxRepository, never()).save(outbox);
        }

        @Test
        @DisplayName("이미 FAILED 처리된 Outbox는 다시 실패 처리하지 않는다")
        void recordFailedAttempt_failedOutbox_isIgnored() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            ReflectionTestUtils.setField(outbox, "status", ProblemEventOutboxStatus.FAILED);
            ReflectionTestUtils.setField(outbox, "retryCount", MAX_RETRY_COUNT);

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.FAILED);
            assertThat(outbox.getRetryCount()).isEqualTo(MAX_RETRY_COUNT);

            verify(problemEventOutboxRepository, never()).save(outbox);
        }
    }

    @Nested
    @DisplayName("재시도 실패 기록")
    class RetryFailure {

        @Test
        @DisplayName("Kafka 발행 실패를 기록하면 retryCount가 1 증가한다")
        void recordFailedAttempt_incrementsRetryCount() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            assertThat(outbox.getRetryCount()).isZero();

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(1);

            verify(problemEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("Kafka 발행 실패를 기록하면 nextAttemptAt이 계산된다")
        void recordFailedAttempt_setsNextAttemptAt() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            assertThat(outbox.getNextAttemptAt()).isNull();

            Instant before = Instant.now();

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            Instant after = Instant.now();

            // then
            assertThat(outbox.getNextAttemptAt()).isNotNull();
            assertThat(outbox.getNextAttemptAt()).isAfterOrEqualTo(before.plusSeconds(FIRST_RETRY_DELAY_SECONDS));
            assertThat(outbox.getNextAttemptAt()).isBeforeOrEqualTo(after.plusSeconds(FIRST_RETRY_DELAY_SECONDS));
        }

        @Test
        @DisplayName("첫 번째 실패 후 재시도 지연 시간은 60초다")
        void recordFailedAttempt_firstFailure_uses60SecondBackoff() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            Instant before = Instant.now();

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            Instant after = Instant.now();

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertBackoff(outbox, before, after, FIRST_RETRY_DELAY_SECONDS);
        }

        @Test
        @DisplayName("두 번째 실패 후 재시도 지연 시간은 120초다")
        void recordFailedAttempt_secondFailure_uses120SecondBackoff() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            ReflectionTestUtils.setField(outbox, "retryCount", 1);

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            Instant before = Instant.now();

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            Instant after = Instant.now();

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(2);
            assertBackoff(outbox, before, after, SECOND_RETRY_DELAY_SECONDS);
        }

        @Test
        @DisplayName("세 번째 실패 후 재시도 지연 시간은 240초다")
        void recordFailedAttempt_thirdFailure_uses240SecondBackoff() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            ReflectionTestUtils.setField(outbox, "retryCount", 2);

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            Instant before = Instant.now();

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            Instant after = Instant.now();

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(3);
            assertBackoff(outbox, before, after, THIRD_RETRY_DELAY_SECONDS);
        }

        @Test
        @DisplayName("네 번째 실패 후 재시도 지연 시간은 480초다")
        void recordFailedAttempt_fourthFailure_uses480SecondBackoff() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            ReflectionTestUtils.setField(outbox, "retryCount", 3);

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            Instant before = Instant.now();

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            Instant after = Instant.now();

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(4);
            assertBackoff(outbox, before, after, FOURTH_RETRY_DELAY_SECONDS);
        }

        @Test
        @DisplayName("최대 재시도 횟수에 도달하기 전에는 PENDING 상태로 되돌린다")
        void recordFailedAttempt_beforeMaxRetry_remainsPending() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            ReflectionTestUtils.setField(outbox, "retryCount", 3);

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(4);
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
            assertThat(outbox.getNextAttemptAt()).isNotNull();

            verify(problemEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("최대 재시도 횟수에 도달하면 FAILED 상태로 전환한다")
        void recordFailedAttempt_reachesMaxRetry_marksFailed() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            ReflectionTestUtils.setField(outbox, "retryCount", 4);

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(MAX_RETRY_COUNT);
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.FAILED);
            assertThat(outbox.getLastError()).isEqualTo(KAFKA_PUBLISH_FAILED);

            verify(problemEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("Kafka 발행 실패 사유를 lastError에 저장한다")
        void recordFailedAttempt_recordsLastError() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);

            // then
            assertThat(outbox.getLastError()).isEqualTo(KAFKA_PUBLISH_FAILED);
        }

        @Test
        @DisplayName("연속 실패할 때 retryCount에 따라 exponential backoff가 증가한다")
        void recordFailedAttempt_multipleFailures_usesExponentialBackoff() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            Instant firstBefore = Instant.now();
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);
            Instant firstAfter = Instant.now();
            Instant firstNextAttemptAt = outbox.getNextAttemptAt();

            reclaim(outbox);
            Instant secondBefore = Instant.now();
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);
            Instant secondAfter = Instant.now();
            Instant secondNextAttemptAt = outbox.getNextAttemptAt();

            reclaim(outbox);
            Instant thirdBefore = Instant.now();
            problemEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), CLAIM_ID, KAFKA_PUBLISH_FAILED);
            Instant thirdAfter = Instant.now();
            Instant thirdNextAttemptAt = outbox.getNextAttemptAt();

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(3);

            assertInstantWithinBackoffWindow(firstNextAttemptAt, firstBefore, firstAfter, FIRST_RETRY_DELAY_SECONDS);
            assertInstantWithinBackoffWindow(secondNextAttemptAt, secondBefore, secondAfter, SECOND_RETRY_DELAY_SECONDS);
            assertInstantWithinBackoffWindow(thirdNextAttemptAt, thirdBefore, thirdAfter, THIRD_RETRY_DELAY_SECONDS);

            assertThat(Duration.between(firstAfter, firstNextAttemptAt).toSeconds())
                    .isLessThan(Duration.between(secondAfter, secondNextAttemptAt).toSeconds());

            assertThat(Duration.between(secondAfter, secondNextAttemptAt).toSeconds())
                    .isLessThan(Duration.between(thirdAfter, thirdNextAttemptAt).toSeconds());

            verify(problemEventOutboxRepository, times(3)).save(outbox);
        }
    }

    @Nested
    @DisplayName("발행 완료 처리")
    class MarkPublished {

        @Test
        @DisplayName("선점한 Outbox를 발행 완료 처리하면 PUBLISHED 상태가 된다")
        void markPublished_changesStatusToPublished() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.markPublished(outbox.getId(), CLAIM_ID);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);

            verify(problemEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("선점한 Outbox를 발행 완료 처리하면 publishedAt이 기록된다")
        void markPublished_recordsPublishedAt() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            Instant before = Instant.now();

            // when
            problemEventOutboxPersistenceService.markPublished(outbox.getId(), CLAIM_ID);

            Instant after = Instant.now();

            // then
            assertThat(outbox.getPublishedAt()).isNotNull();
            assertThat(outbox.getPublishedAt()).isAfterOrEqualTo(before);
            assertThat(outbox.getPublishedAt()).isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("이미 PUBLISHED 상태인 Outbox는 다시 발행 완료 처리하지 않는다")
        void markPublished_alreadyPublished_isIgnored() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();
            Instant originalPublishedAt = Instant.parse("2026-09-23T00:10:00Z");

            outbox.markPublished(CLAIM_ID, originalPublishedAt);

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.markPublished(outbox.getId(), CLAIM_ID);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
            assertThat(outbox.getPublishedAt()).isEqualTo(originalPublishedAt);

            verify(problemEventOutboxRepository, never()).save(outbox);
        }
    }

    @Nested
    @DisplayName("Post Publish 실패 처리")
    class PostPublishFailure {

        @Test
        @DisplayName("Kafka 발행 결과를 확정할 수 없으면 Outbox는 PENDING 상태로 되돌린다")
        void recordPostPublishFailure_keepsPendingStatus() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.recordPostPublishFailure(outbox.getId(), CLAIM_ID, POST_PUBLISH_FAILED);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
            assertThat(outbox.getPublishedAt()).isNull();

            verify(problemEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("post publish 실패 사유를 lastError에 기록한다")
        void recordPostPublishFailure_recordsLastError() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.recordPostPublishFailure(outbox.getId(), CLAIM_ID, POST_PUBLISH_FAILED);

            // then
            assertThat(outbox.getLastError()).isEqualTo(POST_PUBLISH_FAILED);
        }

        @Test
        @DisplayName("post publish 실패를 기록하면 retryCount가 1 증가한다")
        void recordPostPublishFailure_incrementsRetryCount() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            assertThat(outbox.getRetryCount()).isZero();

            // when
            problemEventOutboxPersistenceService.recordPostPublishFailure(outbox.getId(), CLAIM_ID, POST_PUBLISH_FAILED);

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 PUBLISHED 상태인 Outbox에는 post publish 실패를 다시 기록하지 않는다")
        void recordPostPublishFailure_publishedOutbox_isIgnored() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            outbox.markPublished(CLAIM_ID, Instant.parse("2026-09-23T00:10:00Z"));

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.recordPostPublishFailure(outbox.getId(), CLAIM_ID, POST_PUBLISH_FAILED);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);

            verify(problemEventOutboxRepository, never()).save(outbox);
        }
    }

    @Nested
    @DisplayName("강제 FAILED 처리")
    class MarkFailed {

        @Test
        @DisplayName("복구 불가능한 오류이면 Outbox를 FAILED 상태로 변경한다")
        void markFailed_changesStatusToFailed() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.markFailed(outbox.getId(), CLAIM_ID, PAYLOAD_TOO_LARGE);

            // then
            assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.FAILED);
            assertThat(outbox.getLastError()).isEqualTo(PAYLOAD_TOO_LARGE);

            verify(problemEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("강제 FAILED 처리 시 publishedAt은 기록하지 않는다")
        void markFailed_doesNotSetPublishedAt() {
            // given
            ProblemEventOutbox outbox = createClaimedOutbox();

            when(problemEventOutboxRepository.findById(outbox.getId())).thenReturn(Optional.of(outbox));

            // when
            problemEventOutboxPersistenceService.markFailed(outbox.getId(), CLAIM_ID, PAYLOAD_TOO_LARGE);

            // then
            assertThat(outbox.getPublishedAt()).isNull();
        }
    }

    private ProblemEventOutbox createClaimedOutbox() {
        ProblemEventOutbox outbox = createPendingOutbox();
        Instant now = Instant.now();
        outbox.claim(CLAIM_ID, now, now.plus(LEASE_DURATION));
        return outbox;
    }

    /** 실패 기록으로 PENDING이 된 Outbox를 다음 시도를 위해 다시 선점합니다. */
    private void reclaim(ProblemEventOutbox outbox) {
        ReflectionTestUtils.setField(outbox, "nextAttemptAt", null);
        Instant now = Instant.now();
        outbox.claim(CLAIM_ID, now, now.plus(LEASE_DURATION));
    }

    private ProblemEventOutbox createPendingOutbox() {
        ProblemEventOutbox outbox = ProblemEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                """
                {
                  "eventType": "PROBLEM_PUBLISHED"
                }
                """,
                Instant.parse("2026-09-23T00:00:00Z")
        );

        ReflectionTestUtils.setField(outbox, "id", UUID.randomUUID());

        return outbox;
    }

    private void assertBackoff(
            ProblemEventOutbox outbox,
            Instant before,
            Instant after,
            long expectedSeconds
    ) {
        assertThat(outbox.getNextAttemptAt()).isNotNull();
        assertInstantWithinBackoffWindow(outbox.getNextAttemptAt(), before, after, expectedSeconds);
    }

    private void assertInstantWithinBackoffWindow(
            Instant actual,
            Instant before,
            Instant after,
            long expectedSeconds
    ) {
        assertThat(actual).isAfterOrEqualTo(before.plusSeconds(expectedSeconds));
        assertThat(actual).isBeforeOrEqualTo(after.plusSeconds(expectedSeconds));
    }
}