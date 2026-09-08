package com.maesamco.content.problem.infrastructure.messaging.producer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemPublishedKafkaProducerTest {

    private static final String TOPIC =
            "problem-published-events";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ProblemPublishedKafkaProducer producer;

    @BeforeEach
    void setUp() {
        producer =
                new ProblemPublishedKafkaProducer(
                        kafkaTemplate
                );

        ReflectionTestUtils.setField(
                producer,
                "topic",
                TOPIC
        );
    }

    @Test
    @DisplayName(
            "ProblemPublished 발행 시 "
                    + "problemId를 key로 사용하고 Outbox payload를 그대로 전송한다"
    )
    void publish_sendsOutboxPayloadWithProblemIdAsKey() {
        // given
        UUID problemId =
                UUID.randomUUID();

        String payload =
                """
                {
                  "eventId": "event-id",
                  "eventType": "PROBLEM_PUBLISHED"
                }
                """;

        @SuppressWarnings("unchecked")
        SendResult<String, String> sendResult =
                mock(
                        SendResult.class
                );

        CompletableFuture<SendResult<String, String>> future =
                CompletableFuture.completedFuture(
                        sendResult
                );

        when(
                kafkaTemplate.send(
                        TOPIC,
                        problemId.toString(),
                        payload
                )
        ).thenReturn(
                future
        );

        // when
        CompletableFuture<SendResult<String, String>> result =
                producer.publish(
                        problemId,
                        payload
                );

        // then
        assertThat(result)
                .isSameAs(future);

        verify(
                kafkaTemplate
        ).send(
                TOPIC,
                problemId.toString(),
                payload
        );
    }
}
