package com.maesamco.judge.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maesamco.judge.global.config.KafkaConsumerConfig.DltMetricRetryListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.backoff.FixedBackOff;

class DltMetricRetryListenerIntegrationTest {

    @Test
    @DisplayName("실제 DefaultErrorHandler 경로를 거쳐, 재시도 소진 후 recovery 시점에만 "
            + "wrapper가 아닌 root cause exceptionType으로 카운터가 1회 증가한다")
    void incrementsCounterWithRootCauseTypeOnlyAfterRetriesExhausted() {
        // given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> dltKafkaTemplate = mock(KafkaTemplate.class);
        when(dltKafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate);
        // 테스트에서 실제로 1초씩 기다리지 않도록 interval은 0으로
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 3L));
        errorHandler.setRetryListeners(new DltMetricRetryListener(meterRegistry));

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("problem-published-events", 0, 0L, "key", "value");
        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        // 실제 @KafkaListener 실행 경로에서는 리스너 어댑터가 예외를
        // ListenerExecutionFailedException으로 한 번 감싸서 ErrorHandler로 전달한다.
        // root cause 추출 로직(rootCauseSimpleName)이 wrapper가 아니라
        // 실제 원인 타입을 뽑아내는지 검증하기 위해 동일하게 감싸서 흉내낸다.
        IllegalArgumentException rootCause = new IllegalArgumentException("legacy language value");
        ListenerExecutionFailedException wrapped =
                new ListenerExecutionFailedException("Listener failed", rootCause);

        // when - 재시도가 남아있는 동안은 handleRemaining()이 (package-private) RecordInRetryException을 던진다.
        // FixedBackOff(0L, 3L) => 최대 3번 재시도이므로 3번은 예외를 던지고 카운터는 그대로여야 한다.
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(threwOnHandleRemaining(errorHandler, wrapped, record, consumer, container)).isTrue();
            // then - 재시도 도중에는 아직 증가하지 않는다
            assertThat(totalCount(meterRegistry)).isZero();
        }

        // 재시도가 소진된 마지막 호출은 예외 없이 recovery로 이어진다.
        assertThat(threwOnHandleRemaining(errorHandler, wrapped, record, consumer, container)).isFalse();

        // then - 재시도 소진 후 recovery 시점에 정확히 1회 증가하고,
        // exceptionType 라벨은 wrapper(ListenerExecutionFailedException)가 아니라
        // root cause(IllegalArgumentException)로 찍혀야 한다.
        assertThat(totalCount(meterRegistry)).isEqualTo(1.0);
        double taggedCount = meterRegistry.get("judge.kafka.dlt.count")
                .tag("topic", "problem-published-events")
                .tag("exceptionType", "IllegalArgumentException")
                .counter()
                .count();
        assertThat(taggedCount).isEqualTo(1.0);
    }

    private boolean threwOnHandleRemaining(
            DefaultErrorHandler errorHandler,
            RuntimeException exception,
            ConsumerRecord<String, String> record,
            Consumer<?, ?> consumer,
            MessageListenerContainer container) {
        try {
            errorHandler.handleRemaining(exception, List.of(record), consumer, container);
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private double totalCount(SimpleMeterRegistry meterRegistry) {
        return meterRegistry.find("judge.kafka.dlt.count").counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
    }
}