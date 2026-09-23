package com.maesamco.content.infrastructure.adapter.kafka;

import com.maesamco.content.application.port.EventPublishOutcomeUnknownException;
import com.maesamco.content.application.port.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.RetriableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * EventPublisherPort를 Kafka 기반으로 구현합니다.
 * 실제 전달 여부가 불확실한 경우 일반 발행 실패와 구분하여 처리합니다.
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
            throw new EventPublishOutcomeUnknownException(
                    "Kafka 발행 응답 대기 시간(%dms) 초과 — 실제 전달 여부를 알 수 없습니다. topic=%s, key=%s"
                            .formatted(publishTimeoutMs, topic, key), e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishOutcomeUnknownException(
                    "Kafka 발행 응답 대기 중 인터럽트됨 — 실제 전달 여부를 알 수 없습니다. topic=%s, key=%s"
                            .formatted(topic, key), e
            );
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RetriableException) {
                throw new EventPublishOutcomeUnknownException(
                        "Kafka 발행 중 재시도 가능한 오류 — 실제 전달 여부를 알 수 없습니다. topic=%s, key=%s"
                                .formatted(topic, key), e
                );
            }

            throw new IllegalStateException("Kafka 발행 실패. topic=" + topic + ", key=" + key, e);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 발행 실패. topic=" + topic + ", key=" + key, e);
        }
    }
}