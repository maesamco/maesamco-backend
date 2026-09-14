package com.maesamco.content.problem.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import com.maesamco.content.problem.domain.enums.ProblemEventOutboxStatus;
import com.maesamco.content.problem.domain.repository.ProblemEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemEventOutboxStatusServiceTest {

    @Mock
    private ProblemEventOutboxRepository problemEventOutboxRepository;

    private ProblemEventOutboxStatusService statusService;

    @BeforeEach
    void setUp() {
        statusService =
                new ProblemEventOutboxStatusService(
                        problemEventOutboxRepository
                );
    }

    @Test
    @DisplayName(
            "Kafka 발행 성공 시 Outbox를 PUBLISHED 상태로 변경한다"
    )
    void markPublished_changesStatusToPublished() {
        // given
        UUID outboxId =
                UUID.randomUUID();

        Instant occurredAt =
                Instant.parse(
                        "2026-09-08T00:00:00Z"
                );

        Instant publishedAt =
                Instant.parse(
                        "2026-09-08T00:00:10Z"
                );

        ProblemEventOutbox outbox =
                createPendingOutbox(
                        occurredAt
                );

        when(
                problemEventOutboxRepository.findById(
                        outboxId
                )
        ).thenReturn(
                Optional.of(
                        outbox
                )
        );

        // when
        statusService.markPublished(
                outboxId,
                publishedAt
        );

        // then
        assertThat(
                outbox.getStatus()
        ).isEqualTo(
                ProblemEventOutboxStatus.PUBLISHED
        );

        assertThat(
                outbox.getPublishedAt()
        ).isEqualTo(
                publishedAt
        );

        assertThat(
                outbox.getLastError()
        ).isNull();

        assertThat(
                outbox.getRetryCount()
        ).isZero();
    }

    @Test
    @DisplayName(
            "Kafka 발행 실패 시 Outbox를 PENDING 상태로 유지하고 "
                    + "재시도 횟수와 실패 사유를 기록한다"
    )
    void recordFailure_keepsPendingAndIncrementsRetryCount() {
        // given
        UUID outboxId =
                UUID.randomUUID();

        ProblemEventOutbox outbox =
                createPendingOutbox(
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        String safeError =
                "Kafka publish failed";

        when(
                problemEventOutboxRepository.findById(
                        outboxId
                )
        ).thenReturn(
                Optional.of(
                        outbox
                )
        );

        // when
        statusService.recordFailure(
                outboxId,
                safeError,
                10
        );

        // then
        assertThat(
                outbox.getStatus()
        ).isEqualTo(
                ProblemEventOutboxStatus.PENDING
        );

        assertThat(
                outbox.getRetryCount()
        ).isEqualTo(
                1
        );

        assertThat(
                outbox.getLastError()
        ).isEqualTo(
                safeError
        );

        assertThat(
                outbox.getPublishedAt()
        ).isNull();
    }

    @Test
    @DisplayName(
            "Kafka 발행 실패가 최대 재시도 횟수에 도달하면 "
                    + "Outbox를 FAILED 상태로 전환한다"
    )
    void recordFailure_changesStatusToFailed_whenMaxRetryCountReached() {
        // given
        UUID outboxId =
                UUID.randomUUID();

        ProblemEventOutbox outbox =
                createPendingOutbox(
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        String safeError =
                "KAFKA_PUBLISH_FAILED";

        int maxRetryCount = 10;

        when(
                problemEventOutboxRepository.findById(
                        outboxId
                )
        ).thenReturn(
                Optional.of(
                        outbox
                )
        );

        // when
        for (int attempt = 0;
             attempt < maxRetryCount;
             attempt++) {

            statusService.recordFailure(
                    outboxId,
                    safeError,
                    maxRetryCount
            );
        }

        // then
        assertThat(
                outbox.getStatus()
        ).isEqualTo(
                ProblemEventOutboxStatus.FAILED
        );

        assertThat(
                outbox.getRetryCount()
        ).isEqualTo(
                maxRetryCount
        );

        assertThat(
                outbox.getLastError()
        ).isEqualTo(
                safeError
        );

        assertThat(
                outbox.getPublishedAt()
        ).isNull();
    }

    @Test
    @DisplayName(
            "상태를 변경할 Outbox가 존재하지 않으면 BusinessException을 발생시킨다"
    )
    void markPublished_throwsBusinessException_whenOutboxDoesNotExist() {
        // given
        UUID outboxId =
                UUID.randomUUID();

        Instant publishedAt =
                Instant.parse(
                        "2026-09-08T00:00:10Z"
                );

        when(
                problemEventOutboxRepository.findById(
                        outboxId
                )
        ).thenReturn(
                Optional.empty()
        );

        // when & then
        assertThatThrownBy(
                () -> statusService.markPublished(
                        outboxId,
                        publishedAt
                )
        ).isInstanceOf(
                BusinessException.class
        );
    }

    private ProblemEventOutbox createPendingOutbox(
            Instant occurredAt
    ) {
        return ProblemEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                """
                {
                  "eventType": "PROBLEM_PUBLISHED"
                }
                """,
                occurredAt
        );
    }
}
