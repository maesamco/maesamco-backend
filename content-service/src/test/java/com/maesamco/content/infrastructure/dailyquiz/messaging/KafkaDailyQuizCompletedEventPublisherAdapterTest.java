package com.maesamco.content.infrastructure.dailyquiz.messaging;

import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventPublishOutcomeUnknownException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaDailyQuizCompletedEventPublisherAdapterTest {

    private static final String TOPIC =
            "daily-quiz-completed-events";

    private static final long PUBLISH_TIMEOUT_MILLIS =
            100L;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaDailyQuizCompletedEventPublisherAdapter publisher;

    @BeforeEach
    void setUp() {
        publisher =
                new KafkaDailyQuizCompletedEventPublisherAdapter(
                        kafkaTemplate,
                        TOPIC,
                        PUBLISH_TIMEOUT_MILLIS
                );
    }

    @Test
    @DisplayName(
            "DailyQuizCompleted 발행 시 quizAttemptId를 key로 사용하고 "
                    + "Outbox payload를 그대로 전송한다"
    )
    void publish_sendsPayloadWithQuizAttemptIdAsKey() {
        // given
        UUID quizAttemptId =
                UUID.randomUUID();

        String payload =
                """
                {
                  "eventId": "event-id",
                  "eventType": "DAILY_QUIZ_COMPLETED"
                }
                """;

        @SuppressWarnings("unchecked")
        SendResult<String, String> sendResult =
                mock(
                        SendResult.class
                );

        when(
                kafkaTemplate.send(
                        TOPIC,
                        quizAttemptId.toString(),
                        payload
                )
        ).thenReturn(
                CompletableFuture.completedFuture(
                        sendResult
                )
        );

        // when & then
        assertThatCode(
                () -> publisher.publish(
                        quizAttemptId,
                        payload
                )
        ).doesNotThrowAnyException();

        verify(
                kafkaTemplate
        ).send(
                TOPIC,
                quizAttemptId.toString(),
                payload
        );
    }

    @Test
    @DisplayName(
            "Kafka ACK가 제한 시간 안에 오지 않으면 실제 전달 여부를 알 수 없는 예외를 던진다"
    )
    void publish_whenAckTimesOut_throwsOutcomeUnknownException() {
        // given
        UUID quizAttemptId =
                UUID.randomUUID();

        CompletableFuture<SendResult<String, String>> future =
                new CompletableFuture<>();

        when(
                kafkaTemplate.send(
                        TOPIC,
                        quizAttemptId.toString(),
                        "payload"
                )
        ).thenReturn(
                future
        );

        // when & then
        assertThatThrownBy(
                () -> publisher.publish(
                        quizAttemptId,
                        "payload"
                )
        ).isInstanceOf(
                DailyQuizCompletedEventPublishOutcomeUnknownException.class
        ).hasCauseInstanceOf(
                java.util.concurrent.TimeoutException.class
        );
    }

    @Test
    @DisplayName(
            "ACK 대기 중 인터럽트되면 인터럽트 상태를 복원하고 실제 전달 여부를 알 수 없는 예외를 던진다"
    )
    void publish_whenInterrupted_restoresInterruptStatus() {
        // given
        UUID quizAttemptId =
                UUID.randomUUID();

        CompletableFuture<SendResult<String, String>> future =
                new CompletableFuture<>();

        when(
                kafkaTemplate.send(
                        TOPIC,
                        quizAttemptId.toString(),
                        "payload"
                )
        ).thenReturn(
                future
        );

        Thread.currentThread().interrupt();

        try {
            // when & then
            assertThatThrownBy(
                    () -> publisher.publish(
                            quizAttemptId,
                            "payload"
                    )
            ).isInstanceOf(
                    DailyQuizCompletedEventPublishOutcomeUnknownException.class
            ).hasCauseInstanceOf(
                    InterruptedException.class
            );

            assertThat(
                    Thread.currentThread().isInterrupted()
            ).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName(
            "Kafka 발행이 확정 실패하면 IllegalStateException으로 변환한다"
    )
    void publish_whenKafkaRejectsRecord_throwsIllegalStateException() {
        // given
        UUID quizAttemptId =
                UUID.randomUUID();

        RuntimeException cause =
                new RuntimeException(
                        "broker failure"
                );

        when(
                kafkaTemplate.send(
                        TOPIC,
                        quizAttemptId.toString(),
                        "payload"
                )
        ).thenReturn(
                CompletableFuture.failedFuture(
                        cause
                )
        );

        // when & then
        assertThatThrownBy(
                () -> publisher.publish(
                        quizAttemptId,
                        "payload"
                )
        ).isInstanceOf(
                IllegalStateException.class
        ).hasCause(
                cause
        );
    }

    @Test
    @DisplayName(
            "Kafka의 재시도 가능한 오류는 실제 전달 여부를 알 수 없는 예외로 변환한다"
    )
    void publish_whenKafkaReturnsRetriableError_throwsOutcomeUnknownException() {
        // given
        UUID quizAttemptId =
                UUID.randomUUID();

        org.apache.kafka.common.errors.TimeoutException cause =
                new org.apache.kafka.common.errors.TimeoutException(
                        "broker timeout"
                );

        when(
                kafkaTemplate.send(
                        TOPIC,
                        quizAttemptId.toString(),
                        "payload"
                )
        ).thenReturn(
                CompletableFuture.failedFuture(
                        cause
                )
        );

        // when & then
        assertThatThrownBy(
                () -> publisher.publish(
                        quizAttemptId,
                        "payload"
                )
        ).isInstanceOf(
                DailyQuizCompletedEventPublishOutcomeUnknownException.class
        ).hasRootCause(
                cause
        );
    }
}
