package com.maesamco.coaching.infrastructure.kafka;

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
            throw new IllegalStateException(
                    "Kafka 발행 응답 대기 시간(%dms) 초과. topic=%s, key=%s".formatted(publishTimeoutMs, topic, key), e);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 발행 실패. topic=" + topic + ", key=" + key, e);
        }
    }
}
