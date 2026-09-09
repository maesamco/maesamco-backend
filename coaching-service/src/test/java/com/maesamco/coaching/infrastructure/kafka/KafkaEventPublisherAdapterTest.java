package com.maesamco.coaching.infrastructure.kafka;

import com.maesamco.coaching.application.port.EventPublishOutcomeUnknownException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherAdapterTest {

    @Mock
    private KafkaTemplate<String, String> outboxKafkaTemplate;

    @InjectMocks
    private KafkaEventPublisherAdapter kafkaEventPublisherAdapter;

    @BeforeEach
    void setUp() {
        // 타임아웃 테스트가 기본값(3000ms)만큼 기다리지 않도록 짧게 오버라이드
        ReflectionTestUtils.setField(kafkaEventPublisherAdapter, "publishTimeoutMs", 100L);
    }

    @Test
    @DisplayName("제한 시간 안에 응답이 오면 예외 없이 끝난다")
    void publishesSuccessfully() {
        CompletableFuture<SendResult<String, String>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        given(outboxKafkaTemplate.send("topic", "key", "payload")).willReturn(future);

        assertThatCode(() -> kafkaEventPublisherAdapter.publish("topic", "key", "payload"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("응답이 제한 시간 안에 안 오면 EventPublishOutcomeUnknownException으로 감싸서 던진다 — 실제 전달 여부를 모르므로 일반 발행 실패와 구분한다")
    void wrapsTimeoutAsOutcomeUnknown() {
        // 절대 완료되지 않는 future — 브로커 지연/리더 선출 지연 상황을 흉내냄
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        given(outboxKafkaTemplate.send("topic", "key", "payload")).willReturn(future);

        assertThatThrownBy(() -> kafkaEventPublisherAdapter.publish("topic", "key", "payload"))
                .isInstanceOf(EventPublishOutcomeUnknownException.class)
                .hasCauseInstanceOf(TimeoutException.class)
                .hasMessageContaining("100ms");
    }

    @Test
    @DisplayName("응답 대기 중 인터럽트되면 인터럽트 상태를 복원하고 EventPublishOutcomeUnknownException으로 감싸서 던진다")
    void restoresInterruptStatusOnInterruption() {
        // 절대 완료되지 않는 future + 스레드를 미리 인터럽트 상태로 만들어서, get()이 즉시
        // InterruptedException을 던지도록 유도한다.
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        given(outboxKafkaTemplate.send("topic", "key", "payload")).willReturn(future);

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> kafkaEventPublisherAdapter.publish("topic", "key", "payload"))
                    .isInstanceOf(EventPublishOutcomeUnknownException.class)
                    .hasCauseInstanceOf(InterruptedException.class);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // 다른 테스트로 인터럽트 상태가 새어나가지 않도록 정리
        }
    }

    @Test
    @DisplayName("Kafka가 발행 실패로 응답하면 IllegalStateException으로 감싸서 던진다")
    void wrapsPublishFailure() {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("브로커 응답 실패"));
        given(outboxKafkaTemplate.send("topic", "key", "payload")).willReturn(future);

        assertThatThrownBy(() -> kafkaEventPublisherAdapter.publish("topic", "key", "payload"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Kafka send future가 RetriableException으로 완료되면(예: 브로커 쪽 일시적 타임아웃) 확정 실패가 아니라 EventPublishOutcomeUnknownException으로 감싼다")
    void wrapsRetriableKafkaExceptionAsOutcomeUnknown() {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("브로커 응답 지연"));
        given(outboxKafkaTemplate.send("topic", "key", "payload")).willReturn(future);

        assertThatThrownBy(() -> kafkaEventPublisherAdapter.publish("topic", "key", "payload"))
                .isInstanceOf(EventPublishOutcomeUnknownException.class)
                .hasCauseInstanceOf(java.util.concurrent.ExecutionException.class);
    }
}
