package com.maesamco.judge.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.maesamco.judge.global.config.KafkaConsumerConfig.DltMetricRetryListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DltMetricRetryListenerTest {

    @Test
    @DisplayName("recovered 콜백 호출 시 topic/exceptionType 라벨로 카운터가 증가한다")
    void incrementsCounterOnRecovered() {
        // given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DltMetricRetryListener listener = new DltMetricRetryListener(meterRegistry);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("problem-published-events", 0, 0L, "key", "value");
        IllegalArgumentException exception = new IllegalArgumentException("legacy language value");

        // when
        listener.recovered(record, exception);

        // then
        double count = meterRegistry.get("judge.kafka.dlt.count")
                .tag("topic", "problem-published-events")
                .tag("exceptionType", "IllegalArgumentException")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);
    }
}