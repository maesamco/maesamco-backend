package com.maesamco.user.global.config;

import com.maesamco.user.infrastructure.messaging.event.CoachingCompletedEvent;
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
 * User Service가 소비하는 Kafka 이벤트의 역직렬화와 재시도·DLT 정책입니다.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, CoachingCompletedEvent>
    coachingCompletedConsumerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class
        );
        properties.put(
                ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                StringDeserializer.class
        );
        properties.put(
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                JacksonJsonDeserializer.class
        );
        properties.put(
                JacksonJsonDeserializer.TRUSTED_PACKAGES,
                "com.maesamco.user.infrastructure.messaging.event"
        );
        properties.put(
                JacksonJsonDeserializer.VALUE_DEFAULT_TYPE,
                CoachingCompletedEvent.class.getName()
        );
        properties.put(
                JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS,
                false
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<Object, Object>
    coachingCompletedDltKafkaTemplate() {
        Map<Class<?>, org.apache.kafka.common.serialization.Serializer<?>>
                delegates = Map.of(
                        byte[].class,
                        new ByteArraySerializer(),
                        CoachingCompletedEvent.class,
                        new JacksonJsonSerializer<>()
                );

        Map<String, Object> properties = new HashMap<>();
        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        DefaultKafkaProducerFactory<Object, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(properties);
        producerFactory.setValueSerializer(
                new DelegatingByTypeSerializer(delegates)
        );

        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CoachingCompletedEvent>
    coachingCompletedKafkaListenerContainerFactory(
            ConsumerFactory<String, CoachingCompletedEvent>
                    coachingCompletedConsumerFactory,
            KafkaTemplate<Object, Object>
                    coachingCompletedDltKafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, CoachingCompletedEvent>
                factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(coachingCompletedConsumerFactory);

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        coachingCompletedDltKafkaTemplate
                );

        factory.setCommonErrorHandler(
                new DefaultErrorHandler(
                        recoverer,
                        new FixedBackOff(1000L, 3L)
                )
        );

        return factory;
    }
}
