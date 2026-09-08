package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.persistence_service.CoachingEventOutboxPersistenceService;
import com.maesamco.coaching.application.port.EventPublishOutcomeUnknownException;
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
            try {
                relayOne(outbox);
            } catch (Exception e) {
                // relayOne() 내부에서 못 잡은 예외(예: recordFailedAttempt/recordPostPublishFailure
                // 자체가 DB 커넥션 풀 고갈 등으로 실패)가 이 항목 하나 때문에 같은 배치의 나머지
                // outbox까지 막지 않도록 격리한다. findTop100...OrderByCreatedAtAsc가 오래된 순으로
                // 뽑으므로, 여기서 격리하지 않으면 이 outbox가 다음 폴링에서도 계속 맨 앞을 차지하며
                // 뒤의 항목들을 무기한 밀어낼 수 있다(PR #123 심층 재검토, 2026-09-09).
                log.error("[Coaching] Outbox 처리 중 예상치 못한 예외 — 이 항목만 건너뛰고 나머지 배치는 계속 처리. outboxId={}",
                        outbox.getId(), e);
            }
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
        } catch (EventPublishOutcomeUnknownException e) {
            // 응답 대기 시간 초과·인터럽트 등으로 실제 전달 여부를 확인 못한 경우 — 이미 전달됐을
            // 수 있으므로 recordFailedAttempt와 같은 상한으로 FAILED 종료하면 안 된다.
            // recordPostPublishFailure와 동일한 무한 재시도 경로로 보낸다(PR #123 심층 재검토,
            // 2026-09-09 — 처음엔 이 경우도 일반 발행 실패와 같이 취급해서, 마지막 재시도에서
            // 타임아웃이 나면 실제로는 전달된 이벤트를 영구 유실 처리할 위험이 있었다).
            coachingEventOutboxPersistenceService.recordPostPublishFailure(outbox.getId());
            log.error("[Coaching] Outbox 발행 결과를 확인하지 못함 — 재시도 대상으로 표시. outboxId={}, eventType={}",
                    outbox.getId(), outbox.getEventType(), e);
            return;
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
