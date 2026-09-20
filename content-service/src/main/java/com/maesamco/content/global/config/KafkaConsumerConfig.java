package com.maesamco.content.global.config;

import com.maesamco.content.infrastructure.messaging.event.SubmissionJudgedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 이벤트 역직렬화와 Listener 오류 처리를 설정합니다.
 * 처리에 실패한 이벤트는 재시도 후 DLT로 전달합니다.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ===== SubmissionJudged =====

    @Bean
    public ConsumerFactory<String, SubmissionJudgedEvent>
    submissionJudgedConsumerFactory() {
        return createConsumerFactory(SubmissionJudgedEvent.class);
    }

    @Bean
    public KafkaTemplate<Object, Object>
    submissionJudgedDltKafkaTemplate() {
        return createDltKafkaTemplate(SubmissionJudgedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SubmissionJudgedEvent>
    submissionJudgedKafkaListenerContainerFactory(
            ConsumerFactory<String, SubmissionJudgedEvent> submissionJudgedConsumerFactory,
            KafkaTemplate<Object, Object> submissionJudgedDltKafkaTemplate
    ) {
        return createContainerFactory(
                submissionJudgedConsumerFactory,
                submissionJudgedDltKafkaTemplate
        );
    }

    // ===== 공통 빌더 =====

    /**
     * 이벤트 타입별 ConsumerFactory를 생성합니다.
     * 역직렬화 오류도 Listener 오류 처리 흐름에서 다룰 수 있도록 구성합니다.
     */
    private <T> ConsumerFactory<String, T> createConsumerFactory(Class<T> eventType) {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.maesamco.content.infrastructure.messaging.event");
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, eventType.getName());
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * 원본 이벤트와 역직렬화 실패 원문(byte[])을 모두 DLT로 보낼 수 있는
     * KafkaTemplate을 생성합니다.
     */
    private <T> KafkaTemplate<Object, Object> createDltKafkaTemplate(Class<T> eventType) {
        Map<Class<?>, org.apache.kafka.common.serialization.Serializer<?>>
                delegates = Map.of(
                byte[].class,
                new ByteArraySerializer(),
                eventType,
                new JacksonJsonSerializer<>()
        );

        Map<String, Object> producerProps = new HashMap<>();

        producerProps.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        producerProps.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        DefaultKafkaProducerFactory<Object, Object> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);

        producerFactory.setValueSerializer(
                new DelegatingByTypeSerializer(delegates)
        );

        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Listener 처리 실패 시 1초 간격으로 3회 재시도하고,
     * 최종 실패한 이벤트를 DLT로 전달합니다.
     */
    @SafeVarargs
    private <T> ConcurrentKafkaListenerContainerFactory<String, T>
    createContainerFactory(
            ConsumerFactory<String, T> consumerFactory,
            KafkaTemplate<Object, Object> dltKafkaTemplate,
            Class<? extends Exception>... notRetryableExceptions
    ) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        dltKafkaTemplate
                );

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        new FixedBackOff(1000L, 3L)
                );

        if (notRetryableExceptions.length > 0) {
            errorHandler.addNotRetryableExceptions(notRetryableExceptions);
        }

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}