package com.maesamco.coaching.infrastructure.kafka;

import com.maesamco.coaching.application.port.EventPublishOutcomeUnknownException;
import com.maesamco.coaching.application.port.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Judge Service의 KafkaEventPublisherAdapter(이슈 #63)와 동일한 설계 — 브로커 지연/리더
 * 선출 등으로 응답이 안 오는 상황에서 무제한 대기하지 않도록 명시적 타임아웃을 건다.
 *
 * 응답 대기 시간 초과(TimeoutException)와 대기 중 인터럽트(InterruptedException)는 "발행이
 * 실패했다"는 뜻이 아니라 "결과를 확인하지 못했다"는 뜻이다 — 전송 자체는 백그라운드에서 계속
 * 진행돼 실제로는 이미 전달됐을 수 있다. 그래서 이 둘은 일반 발행 실패와 구분되는
 * {@link EventPublishOutcomeUnknownException}으로 던진다(PR #123 심층 재검토, 2026-09-09
 * — 처음엔 둘 다 같은 IllegalStateException으로 묶었는데, 그러면 재시도 상한 소진 시
 * recordFailedAttempt()가 FAILED로 종료해버려서 실제로는 전달된 이벤트를 영구 유실 처리할
 * 위험이 있었다).
 */
@Component
@RequiredArgsConstructor
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    @Value("${outbox.relay.publish-timeout-ms:3000}")
    private long publishTimeoutMs;

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            outboxKafkaTemplate.send(topic, key, payload).get(publishTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new EventPublishOutcomeUnknownException(
                    "Kafka 발행 응답 대기 시간(%dms) 초과 — 실제 전달 여부를 알 수 없습니다. topic=%s, key=%s"
                            .formatted(publishTimeoutMs, topic, key), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishOutcomeUnknownException(
                    "Kafka 발행 응답 대기 중 인터럽트됨 — 실제 전달 여부를 알 수 없습니다. topic=%s, key=%s"
                            .formatted(topic, key), e);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 발행 실패. topic=" + topic + ", key=" + key, e);
        }
    }
}
