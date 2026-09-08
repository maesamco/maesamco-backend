package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.persistence_service.CoachingEventOutboxPersistenceService;
import com.maesamco.coaching.application.port.EventPublisherPort;
import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.OutboxStatus;
import com.maesamco.coaching.domain.repository.CoachingEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Outbox Relay — p_coaching_event_outboxes를 폴링해서 Kafka로 CoachingCompleted 발행.
 * Judge Service의 SubmissionEventRelayFacade(이슈 #63)와 동일한 설계.
 *
 * 외부 호출(Kafka 발행) + DB 쓰기(markPublished)가 섞인 유스케이스라 Facade 패턴 사용.
 * 핵심 규칙대로 여기엔 @Transactional을 안 붙인다 — 발행은 네트워크 호출이라 응답이 늦거나
 * 안 올 수 있는데 트랜잭션 안에서 부르면 DB 커넥션을 쥔 채 대기하게 되기 때문입니다.
 *
 * 이 Outbox는 지금 event_type이 "CoachingCompleted" 하나뿐이라(Flyway V8 CHECK 제약과
 * 대응) Judge Service처럼 토픽을 event_type별로 분기하지 않는다 — 두 번째 이벤트 타입이
 * 생기면 그때 분기 로직을 추가한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CoachingEventRelayFacade {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final CoachingEventOutboxRepository coachingEventOutboxRepository;
    private final CoachingEventOutboxPersistenceService coachingEventOutboxPersistenceService;
    private final EventPublisherPort eventPublisherPort;

    @Value("${spring.kafka.topic.coaching-completed}")
    private String coachingCompletedTopic;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void relay() {
        List<CoachingEventOutbox> pending =
                coachingEventOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (CoachingEventOutbox outbox : pending) {
            relayOne(outbox);
        }
    }

    private void relayOne(CoachingEventOutbox outbox) {
        // Kafka 발행 시도
        try {
            eventPublisherPort.publish(
                    coachingCompletedTopic,
                    outbox.getAggregateId().toString(),
                    serialize(outbox.getPayload())
            );
        } catch (Exception e) {
            // 1. 발행 실패 — 재시도 상한 안이면 status는 PENDING 그대로 둬서 다음 폴링 주기에 재시도.
            // 2. 상한 소진 시 recordFailedAttempt 내부에서 FAILED로 종료 처리.
            // 재시도로 인한 중복 발행 가능성은 User Service 소비자 쪽 멱등 처리로 대응.
            coachingEventOutboxPersistenceService.recordFailedAttempt(outbox);
            if (outbox.getStatus() != OutboxStatus.FAILED) {
                log.error("[Coaching] Outbox 발행 실패 — 다음 폴링에서 재시도. outboxId={}, eventType={}, attemptCount={}",
                        outbox.getId(), outbox.getEventType(), outbox.getAttemptCount(), e);
            }
            return;
        }
        // 발행 자체는 성공하여 상태 전이 + DB 저장 시도
        try {
            coachingEventOutboxPersistenceService.markPublished(outbox);
            log.info("[Coaching] Outbox 발행 성공. outboxId={}, eventType={}, aggregateId={}",
                    outbox.getId(), outbox.getEventType(), outbox.getAggregateId());
        } catch (Exception e) {
            coachingEventOutboxPersistenceService.recordPostPublishFailure(outbox.getId());
            log.error("[Coaching] Kafka 발행은 성공했으나 후처리(Outbox 완료 표시) 실패 — "
                            + "재시도 대상으로 표시. outboxId={}, eventType={}",
                    outbox.getId(), outbox.getEventType(), e);
        }
    }

    private String serialize(JsonNode payload) {
        return JSON_MAPPER.writeValueAsString(payload);
    }
}
