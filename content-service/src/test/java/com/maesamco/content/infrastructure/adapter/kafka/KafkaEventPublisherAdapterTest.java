package com.maesamco.content.infrastructure.adapter.kafka;

import com.maesamco.content.application.port.EventPublishOutcomeUnknownException;
import org.apache.kafka.common.errors.NetworkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherAdapterTest {

    private static final String TOPIC = "problem-published-events";
    private static final String KEY = "problem-id";
    private static final String PAYLOAD = """
            {
              "eventType": "PROBLEM_PUBLISHED"
            }
            """;

    @Mock
    private KafkaTemplate<String, String> outboxKafkaTemplate;

    private KafkaEventPublisherAdapter kafkaEventPublisherAdapter;

    @BeforeEach
    void setUp() {
        kafkaEventPublisherAdapter = new KafkaEventPublisherAdapter(outboxKafkaTemplate);
        ReflectionTestUtils.setField(kafkaEventPublisherAdapter, "publishTimeoutMs", 5000L);
    }

    @Test
    @DisplayName("Kafka 발행이 성공하면 정상적으로 종료한다")
    void publish_success() {
        // given
        SendResult<String, String> sendResult = new SendResult<>(null, null);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);

        when(outboxKafkaTemplate.send(TOPIC, KEY, PAYLOAD)).thenReturn(future);

        // when
        kafkaEventPublisherAdapter.publish(TOPIC, KEY, PAYLOAD);

        // then
        verify(outboxKafkaTemplate).send(TOPIC, KEY, PAYLOAD);
    }

    @Test
    @DisplayName("Kafka 발행 응답 대기 시간이 초과되면 결과 불명확 예외를 발생시킨다")
    void publish_timeout_throwsOutcomeUnknownException() {
        // given
        ReflectionTestUtils.setField(kafkaEventPublisherAdapter, "publishTimeoutMs", 1L);

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();

        when(outboxKafkaTemplate.send(TOPIC, KEY, PAYLOAD)).thenReturn(future);

        // when & then
        assertThatThrownBy(() -> kafkaEventPublisherAdapter.publish(TOPIC, KEY, PAYLOAD))
                .isInstanceOf(EventPublishOutcomeUnknownException.class)
                .hasMessageContaining("Kafka 발행 응답 대기 시간(1ms) 초과")
                .hasMessageContaining("topic=" + TOPIC)
                .hasMessageContaining("key=" + KEY)
                .hasCauseInstanceOf(TimeoutException.class);

        verify(outboxKafkaTemplate).send(TOPIC, KEY, PAYLOAD);
    }

    @Test
    @DisplayName("Kafka 발행 응답 대기 중 인터럽트되면 인터럽트 상태를 복구하고 결과 불명확 예외를 발생시킨다")
    void publish_interrupted_restoresInterruptAndThrowsOutcomeUnknownException() {
        // given
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();

        when(outboxKafkaTemplate.send(TOPIC, KEY, PAYLOAD)).thenReturn(future);

        try {
            Thread.currentThread().interrupt();

            // when
            Throwable thrown = catchThrowable(
                    () -> kafkaEventPublisherAdapter.publish(TOPIC, KEY, PAYLOAD)
            );

            // then
            assertThat(thrown)
                    .isInstanceOf(EventPublishOutcomeUnknownException.class)
                    .hasMessageContaining("Kafka 발행 응답 대기 중 인터럽트됨")
                    .hasMessageContaining("topic=" + TOPIC)
                    .hasMessageContaining("key=" + KEY);

            assertThat(thrown.getCause()).isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();

            verify(outboxKafkaTemplate).send(TOPIC, KEY, PAYLOAD);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("Kafka 발행 중 재시도 가능한 오류가 발생하면 결과 불명확 예외를 발생시킨다")
    void publish_retriableFailure_throwsOutcomeUnknownException() {
        // given
        NetworkException publishFailure = new NetworkException("temporary network failure");

        CompletableFuture<SendResult<String, String>> future =
                CompletableFuture.failedFuture(publishFailure);

        when(outboxKafkaTemplate.send(TOPIC, KEY, PAYLOAD)).thenReturn(future);

        // when
        Throwable thrown = catchThrowable(
                () -> kafkaEventPublisherAdapter.publish(TOPIC, KEY, PAYLOAD)
        );

        // then
        assertThat(thrown)
                .isInstanceOf(EventPublishOutcomeUnknownException.class)
                .hasMessageContaining("Kafka 발행 중 재시도 가능한 오류")
                .hasMessageContaining("topic=" + TOPIC)
                .hasMessageContaining("key=" + KEY);

        assertThat(thrown.getCause()).isInstanceOf(ExecutionException.class);
        assertThat(thrown.getCause().getCause()).isSameAs(publishFailure);

        verify(outboxKafkaTemplate).send(TOPIC, KEY, PAYLOAD);
    }

    @Test
    @DisplayName("Kafka 발행 중 재시도 불가능한 오류가 발생하면 발행 실패 예외를 발생시킨다")
    void publish_nonRetriableFailure_throwsIllegalStateException() {
        // given
        IllegalArgumentException publishFailure = new IllegalArgumentException("invalid record");

        CompletableFuture<SendResult<String, String>> future =
                CompletableFuture.failedFuture(publishFailure);

        when(outboxKafkaTemplate.send(TOPIC, KEY, PAYLOAD)).thenReturn(future);

        // when
        Throwable thrown = catchThrowable(
                () -> kafkaEventPublisherAdapter.publish(TOPIC, KEY, PAYLOAD)
        );

        // then
        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 발행 실패. topic=" + TOPIC + ", key=" + KEY);

        assertThat(thrown.getCause()).isInstanceOf(ExecutionException.class);
        assertThat(thrown.getCause().getCause()).isSameAs(publishFailure);

        verify(outboxKafkaTemplate).send(TOPIC, KEY, PAYLOAD);
    }

    @Test
    @DisplayName("KafkaTemplate 호출 자체에서 예외가 발생하면 발행 실패 예외를 발생시킨다")
    void publish_sendFailure_throwsIllegalStateException() {
        // given
        IllegalStateException publishFailure = new IllegalStateException("Kafka unavailable");

        when(outboxKafkaTemplate.send(TOPIC, KEY, PAYLOAD)).thenThrow(publishFailure);

        // when & then
        assertThatThrownBy(() -> kafkaEventPublisherAdapter.publish(TOPIC, KEY, PAYLOAD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 발행 실패. topic=" + TOPIC + ", key=" + KEY)
                .hasCause(publishFailure);

        verify(outboxKafkaTemplate).send(TOPIC, KEY, PAYLOAD);
    }
}