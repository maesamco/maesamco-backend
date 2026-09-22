package com.maesamco.judge.global.config;

import com.maesamco.judge.application.exception.InvalidProblemPublishedEventException;
import com.maesamco.judge.infrastructure.messaging.consumer.JudgeRequestedConsumer;
import com.maesamco.judge.infrastructure.messaging.consumer.ProblemPublishedConsumer;
import com.maesamco.judge.infrastructure.messaging.event.JudgeRequestedEvent;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private static final String DLT_METRIC_NAME = "judge.kafka.dlt.count";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.topic.judge-requested:judge-requested-events}")
    private String judgeRequestedTopicName;

    // ⚠️ 부하테스트로 발견 — 기존엔 이 토픽이 Kafka 브로커 기본값(파티션 1개)으로
    // 자동 생성되고 있었다. concurrency를 아무리 올려도 파티션이 1개면 리스너 스레드
    // 하나만 실제로 일하므로(나머지는 대기), concurrency 설정과 반드시 짝을 맞춰야 한다.
    // KafkaAdmin은 기존 토픽의 파티션 수가 부족하면 자동으로 늘려준다(줄이지는 못함).
    @Bean
    public NewTopic judgeRequestedTopic() {
        return TopicBuilder.name(judgeRequestedTopicName)
                .partitions(4)
                .replicas(1)
                .build();
    }

    private final MeterRegistry meterRegistry;

    // ===== ProblemPublished =====

    @Bean
    public ConsumerFactory<String, ProblemPublishedEvent> problemPublishedConsumerFactory() {
        return createConsumerFactory(ProblemPublishedEvent.class);
    }

    @Bean
    public KafkaTemplate<Object, Object> problemPublishedDltKafkaTemplate() {
        return createDltKafkaTemplate(ProblemPublishedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProblemPublishedEvent>
    problemPublishedKafkaListenerContainerFactory(
            ConsumerFactory<String, ProblemPublishedEvent> problemPublishedConsumerFactory,
            KafkaTemplate<Object, Object> problemPublishedDltKafkaTemplate
    ) {
        return createContainerFactory(
                problemPublishedConsumerFactory,
                problemPublishedDltKafkaTemplate,
                ProblemPublishedConsumer.UnsupportedProblemPublishedEventVersionException.class,
                InvalidProblemPublishedEventException.class
        );
    }

    // ===== JudgeRequested =====

    @Bean
    public ConsumerFactory<String, JudgeRequestedEvent> judgeRequestedConsumerFactory() {
        return createConsumerFactory(JudgeRequestedEvent.class);
    }

    @Bean
    public KafkaTemplate<Object, Object> judgeRequestedDltKafkaTemplate() {
        return createDltKafkaTemplate(JudgeRequestedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, JudgeRequestedEvent>
    judgeRequestedKafkaListenerContainerFactory(
            ConsumerFactory<String, JudgeRequestedEvent> judgeRequestedConsumerFactory,
            KafkaTemplate<Object, Object> judgeRequestedDltKafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, JudgeRequestedEvent> factory = createContainerFactory(
                judgeRequestedConsumerFactory,
                judgeRequestedDltKafkaTemplate,
                JudgeRequestedConsumer.UnsupportedJudgeRequestedEventVersionException.class);
        // ⚠️ 부하테스트로 발견 — concurrency 미설정(기본값 1) + Judge0ExecutionAdapter의
        // .block() 동기 대기가 겹쳐서, judge-service가 "한 번에 딱 1건"만 처리 가능한
        // 구조였다. 이게 600명 부하 시 judge0 워커/큐를 아무리 늘리거나 인스턴스를
        // 스케일업해도(t3.large→t3.xlarge) 에러율·응답시간이 전혀 개선 안 됐던 근본 원인.
        // 파티션 수(위 judgeRequestedTopic 빈 참고)와 맞춰 4로 설정 — judge0 기본 워커 수(4)와도
        // 맞물리게 해서, judge0가 실제로 소화 가능한 만큼만 동시에 요청이 나가도록 함.
        factory.setConcurrency(4);
        return factory;
    }

    // ===== 공통 빌더 =====

    private <T> ConsumerFactory<String, T> createConsumerFactory(Class<T> eventType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.maesamco.judge.infrastructure.messaging.event");
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, eventType.getName());
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    private <T> KafkaTemplate<Object, Object> createDltKafkaTemplate(Class<T> eventType) {
        Map<Class<?>, org.apache.kafka.common.serialization.Serializer<?>> delegates = Map.of(
                byte[].class, new ByteArraySerializer(),
                eventType, new JacksonJsonSerializer<>()
        );

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        DefaultKafkaProducerFactory<Object, Object> pf = new DefaultKafkaProducerFactory<>(producerProps);
        pf.setValueSerializer(new DelegatingByTypeSerializer(delegates));

        return new KafkaTemplate<>(pf);
    }

    @SafeVarargs
    private <T> ConcurrentKafkaListenerContainerFactory<String, T> createContainerFactory(
            ConsumerFactory<String, T> consumerFactory,
            KafkaTemplate<Object, Object> dltKafkaTemplate,
            Class<? extends Exception>... notRetryableExceptions
    ) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        errorHandler.setRetryListeners(new DltMetricRetryListener(meterRegistry));

        if (notRetryableExceptions.length > 0) {
            errorHandler.addNotRetryableExceptions(notRetryableExceptions);
        }
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    static class DltMetricRetryListener implements RetryListener {
        private final MeterRegistry meterRegistry;

        DltMetricRetryListener(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
            // 재시도 도중 호출 — 여기선 계측할 필요 없음, 최종 DLT 적재 시점(recovered)만 카운트
        }

        @Override
        public void recovered(ConsumerRecord<?,?> record, Exception e) {
            String exceptionType = e != null ? rootCauseSimpleName(e) : "unknown";
            meterRegistry.counter(DLT_METRIC_NAME,
                    "topic", record.topic(),
                    "exceptionType", exceptionType).increment();
        }

        private String rootCauseSimpleName(Throwable ex) {
            Throwable cause = ex;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            return cause.getClass().getSimpleName();
        }
    }
}