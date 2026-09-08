package com.maesamco.judge.infrastructure.adapter.kafka;

import com.maesamco.judge.application.port.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            outboxKafkaTemplate.send(topic, key, payload).get();
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 발행 실패. topic=" + topic + ", key=" + key, e);
        }
    }
}
