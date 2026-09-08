package com.maesamco.judge.application.facade;

import com.maesamco.judge.application.persistence_service.SubmissionEventOutboxPersistenceService;
import com.maesamco.judge.application.port.EventPublisherPort;
import com.maesamco.judge.domain.entity.OutboxStatus;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@RequiredArgsConstructor
@Slf4j
public class SubmissionEventRelayFacade {

    private static final String JUDGE_REQUESTED_EVENT_TYPE = "JudgeRequested";

    private final SubmissionEventOutboxRepository submissionEventOutboxRepository;
    private final SubmissionEventOutboxPersistenceService submissionEventOutboxPersistenceService;
    private final EventPublisherPort eventPublisherPort;

    @Value("${spring.kafka.topic.judge-requested}")
    private String judgeRequestedTopic;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void relay() {
        List<SubmissionEventOutbox> pending =
                submissionEventOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (SubmissionEventOutbox outbox : pending) {
            relayOne(outbox);
        }
    }

    private void relayOne(SubmissionEventOutbox outbox) {
        // 토픽 분기
        String topic = resolveTopic(outbox.getEventType());
        if (topic == null) {
            // 아직 처리할 줄 모르는 event_type — 재시도해도 결과가 달라지지 않는 영구적 실패이므로
            // 상한 소진을 기다리지 않고 즉시 FAILED로 종료해서 PENDING 큐를 막지 않도록 합니다.
            // 추후 작업에서 SubmissionJudged를 추가할 때 여기도 채워질 예정입니다!! (TODO).
            submissionEventOutboxPersistenceService.markUnsupportedEventType(outbox);
            return;
        }

        // Kafka 발행 시도
        try {
            eventPublisherPort.publish(topic, outbox.getAggregateId().toString(), outbox.getPayload());
        } catch (Exception e) {
            // 1. 발행 실패 — 재시도 상한 안이면 status는 PENDING 그대로 둬서 다음 폴링 주기에 재시도.
            // 2. 상한 소진 시 recordFailedAttempt 내부에서 FAILED로 종료 처리.
            // 재시도로 인한 중복 발행 가능성은 Worker 쪽 멱등 처리로 대응.
            submissionEventOutboxPersistenceService.recordFailedAttempt(outbox);
            if (outbox.getStatus() != OutboxStatus.FAILED) {
                log.error("[Judge] Outbox 발행 실패 — 다음 폴링에서 재시도. outboxId={}, eventType={}, attemptCount={}",
                        outbox.getId(), outbox.getEventType(), outbox.getAttemptCount(), e);
            }
            return;
        }
        // 발행 자체는 성공하여 상태 전이 + DB 저장 시도
        try {
            submissionEventOutboxPersistenceService.markPublished(outbox);
            log.info("[Judge] Outbox 발행 성공. outboxId={}, eventType={}, aggregateId={}",
                    outbox.getId(), outbox.getEventType(), outbox.getAggregateId());
        } catch (Exception e) {
            submissionEventOutboxPersistenceService.recordPostPublishFailure(outbox.getId());
            log.error("[Judge] Kafka 발행은 성공했으나 후처리(Outbox 완료/Submission 전이) 실패 — "
                            + "재시도 대상으로 표시. outboxId={}, eventType={}",
                    outbox.getId(), outbox.getEventType(), e);
        }
    }

    private String resolveTopic(String eventType) {
        if (JUDGE_REQUESTED_EVENT_TYPE.equals(eventType)) {
            return judgeRequestedTopic;
        }
        return null;
    }
}