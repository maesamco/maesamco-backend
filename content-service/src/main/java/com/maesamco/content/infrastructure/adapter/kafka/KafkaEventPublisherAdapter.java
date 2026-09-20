package com.maesamco.content.infrastructure.adapter.kafka;

import com.maesamco.content.application.port.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * EventPublisherPort를 Kafka 기반으로 구현하는 Adapter입니다.
 * Outbox에 저장된 JSON payload를 지정된 topic과 key로 발행합니다.
 */
@Component
@RequiredArgsConstructor
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    @Value("${outbox.problem-published.relay.publish-timeout-ms:5000}")
    private long publishTimeoutMs;

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            outboxKafkaTemplate.send(topic, key, payload).get(publishTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Kafka 발행 응답 대기 시간(%dms) 초과. topic=%s, key=%s"
                            .formatted(publishTimeoutMs, topic, key),
                    e
            );
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 발행 실패. topic=" + topic + ", key=" + key, e);
        }
    }
}