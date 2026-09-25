package com.maesamco.judge.application.facade;

import com.maesamco.judge.application.persistence_service.SubmissionEventOutboxPersistenceService;
import com.maesamco.judge.application.port.EventPublisherPort;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox Relay — p_submission_event_outboxes를 폴링해서 Kafka로 발행.
 *
 * 외부 호출(Kafka 발행) + DB 쓰기(markPublished)가 섞인 유스케이스라 Facade 패턴 사용.
 * 핵심 규칙대로 여기엔 @Transactional을 안 붙인다 — 발행은 네트워크 호출이라 응답이 늦거나
 * 안 올 수 있는데 트랜잭션 안에서 부르면 DB 커넥션을 쥔 채 대기하게 되기 때문입니다.
 *
 * 이 Outbox는 Submission 애그리거트 전용 발행 대기함이라 event_type으로 토픽을 분기합니다.
 */
@Component
@Slf4j
public class SubmissionEventRelayFacade {

    private static final String JUDGE_REQUESTED_EVENT_TYPE = "JudgeRequested";
    private static final String SUBMISSION_JUDGED_EVENT_TYPE = "SubmissionJudged";
    private static final long MIN_LEASE_SAFETY_MARGIN_MILLIS = 1_000L;
    private static final int MAX_BATCH_SIZE = 100;

    private final SubmissionEventOutboxPersistenceService submissionEventOutboxPersistenceService;
    private final EventPublisherPort eventPublisherPort;
    private final String judgeRequestedTopic;
    private final String submissionJudgedTopic;
    private final Duration leaseDuration;

    /**
     * 이슈 #272 — 발행 직전에 행을 선점(claim)한 Worker만 Kafka로 발행한다. 선점 유효시간(lease)이 발행 타임아웃보다
     * 짧으면 응답을 기다리는 중에 lease가 만료돼 다른 Worker가 재선점하고 중복 발행하므로 생성자에서 검증한다.
     */
    public SubmissionEventRelayFacade(
            SubmissionEventOutboxPersistenceService submissionEventOutboxPersistenceService,
            EventPublisherPort eventPublisherPort,
            @Value("${spring.kafka.topic.judge-requested}") String judgeRequestedTopic,
            @Value("${spring.kafka.topic.submission-judged}") String submissionJudgedTopic,
            @Value("${outbox.relay.lease-duration-ms:300000}") long leaseDurationMillis,
            @Value("${outbox.relay.publish-timeout-ms:3000}") long publishTimeoutMillis
    ) {
        if (leaseDurationMillis - publishTimeoutMillis < MIN_LEASE_SAFETY_MARGIN_MILLIS) {
            throw new IllegalArgumentException(
                    "선점 유효시간(outbox.relay.lease-duration-ms=%d)은 발행 타임아웃(outbox.relay.publish-timeout-ms=%d)보다 "
                            .formatted(leaseDurationMillis, publishTimeoutMillis)
                            + "최소 %dms 길어야 합니다.".formatted(MIN_LEASE_SAFETY_MARGIN_MILLIS));
        }
        this.submissionEventOutboxPersistenceService = submissionEventOutboxPersistenceService;
        this.eventPublisherPort = eventPublisherPort;
        this.judgeRequestedTopic = judgeRequestedTopic;
        this.submissionJudgedTopic = submissionJudgedTopic;
        this.leaseDuration = Duration.ofMillis(leaseDurationMillis);
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void relay() {
        // 한 건씩 새 lease로 선점한다 — 배치 전체를 같은 lease로 묶어 선점하면 뒤쪽 항목의 lease가 발행 순서를
        // 기다리는 동안 만료돼 다른 Worker가 재선점·중복 발행할 수 있다(coaching #280).
        for (int processed = 0; processed < MAX_BATCH_SIZE; processed++) {
            UUID claimId = UUID.randomUUID();
            Optional<SubmissionEventOutbox> claimed = claimNextSafely(claimId);
            if (claimed.isEmpty()) {
                return;
            }
            SubmissionEventOutbox outbox = claimed.get();

            try {
                relayOne(outbox, claimId);
            } catch (Exception e) {
                // 한 건의 예외가 같은 배치의 나머지를 막지 않게 격리한다. 결과를 기록하지 못했다면 lease 만료 후 재선점된다.
                if (e instanceof ObjectOptimisticLockingFailureException) {
                    log.warn("[Judge] Outbox 결과 기록 중 낙관적 락 충돌 — 다른 Worker가 재선점해 처리한 것으로 보고 건너뜀. "
                            + "outboxId={}, eventType={}", outbox.getId(), outbox.getEventType());
                } else {
                    log.error("[Judge] Outbox 처리 중 예상치 못한 예외 — 이 항목만 건너뛰고 나머지 배치는 계속 처리. "
                            + "lease 만료 후 재선점됩니다. outboxId={}", outbox.getId(), e);
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                log.warn("[Judge] 인터럽트 감지 — 남은 Outbox 배치 처리를 중단합니다.");
                return;
            }
        }
    }

    private Optional<SubmissionEventOutbox> claimNextSafely(UUID claimId) {
        try {
            return submissionEventOutboxPersistenceService.claimNext(claimId, leaseDuration);
        } catch (Exception e) {
            log.error("[Judge] Outbox 선점 중 예외 — 이번 폴링을 중단합니다.", e);
            return Optional.empty();
        }
    }

    private void relayOne(SubmissionEventOutbox outbox, UUID claimId) {
        String topic = resolveTopic(outbox.getEventType());
        if (topic == null) {
            // 아직 처리할 줄 모르는 event_type — 재시도해도 결과가 달라지지 않는 영구적 실패이므로
            // 상한 소진을 기다리지 않고 즉시 FAILED로 종료해서 PENDING 큐를 막지 않도록 합니다.
            submissionEventOutboxPersistenceService.markUnsupportedEventType(outbox.getId(), claimId);
            return;
        }

        // Kafka 발행 시도
        try {
            eventPublisherPort.publish(topic, outbox.getAggregateId().toString(), outbox.getPayload());
        } catch (Exception e) {
            // 재시도 상한 안이면 선점을 풀어 PENDING으로 되돌려 다음 폴링에서 재시도하고, 상한 소진 시 FAILED로 종료한다.
            // 재시도로 인한 중복 발행 가능성은 Worker 쪽 멱등 처리로 대응.
            submissionEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), claimId);
            log.error("[Judge] Outbox 발행 실패 — 상한 전이면 다음 폴링에서 재시도, 상한 도달이면 FAILED 처리됨. "
                    + "outboxId={}, eventType={}", outbox.getId(), outbox.getEventType(), e);
            return;
        }
        // 발행 자체는 성공하여 상태 전이 + DB 저장 시도
        try {
            boolean updated = submissionEventOutboxPersistenceService.markPublished(outbox.getId(), claimId);
            if (updated) {
                log.info("[Judge] Outbox 발행 성공. outboxId={}, eventType={}, aggregateId={}",
                        outbox.getId(), outbox.getEventType(), outbox.getAggregateId());
            }
        } catch (Exception e) {
            submissionEventOutboxPersistenceService.recordPostPublishFailure(outbox.getId(), claimId);
            log.error("[Judge] Kafka 발행은 성공했으나 후처리(Outbox 완료/Submission 전이) 실패 — "
                            + "재시도 대상으로 표시. outboxId={}, eventType={}",
                    outbox.getId(), outbox.getEventType(), e);
        }
    }

    private String resolveTopic(String eventType) {
        if (JUDGE_REQUESTED_EVENT_TYPE.equals(eventType)) {
            return judgeRequestedTopic;
        }

        if (SUBMISSION_JUDGED_EVENT_TYPE.equals(eventType)) {
            return submissionJudgedTopic;
        }
        return null;
    }
}
