package com.maesamco.content.problem.infrastructure.messaging.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ProblemPublished Outbox 이벤트를 Kafka로 전송합니다.
 *
 * <p>Outbox에 이미 직렬화된 JSON payload를 그대로 Kafka value로 사용하며,
 * problemId 문자열을 Kafka key로 사용합니다.</p>
 *
 * <p>이 컴포넌트는 Kafka 전송만 담당합니다.
 * Outbox의 PUBLISHED/PENDING 상태 변경과 재시도 횟수 관리는
 * Outbox Relay가 담당합니다.</p>
 */
@Component
@RequiredArgsConstructor
public class ProblemPublishedKafkaProducer {

    @Qualifier("problemPublishedKafkaTemplate")
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value(
            "${spring.kafka.topic.problem-published:problem-published-events}"
    )
    private String topic;

    /**
     * ProblemPublished 이벤트 payload를 Kafka로 전송합니다.
     *
     * @param problemId Kafka message key로 사용할 문제 식별자
     * @param payload   Outbox에 저장된 ProblemPublished JSON payload
     * @return Kafka broker 전송 결과 Future
     */
    public CompletableFuture<SendResult<String, String>> publish(
            UUID problemId,
            String payload
    ) {
        Objects.requireNonNull(
                problemId,
                "problemId must not be null"
        );

        Objects.requireNonNull(
                payload,
                "payload must not be null"
        );

        return kafkaTemplate.send(
                topic,
                problemId.toString(),
                payload
        );
    }
}
