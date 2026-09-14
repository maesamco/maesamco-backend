package com.maesamco.content.global.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Content Service의 Kafka Producer 설정입니다.
 *
 * <p>ProblemPublished 이벤트는 Outbox에 이미 JSON 문자열로
 * 직렬화되어 저장되므로 Kafka 전송 시 별도의 JSON 직렬화를
 * 다시 수행하지 않습니다.</p>
 */
@Configuration
public class KafkaProducerConfig {

    private final String bootstrapServers;

    public KafkaProducerConfig(
            @Value(
                    "${spring.kafka.bootstrap-servers:localhost:9092}"
            )
            String bootstrapServers
    ) {
        this.bootstrapServers =
                bootstrapServers;
    }

    /**
     * ProblemPublished Outbox 이벤트를 발행하는 KafkaTemplate입니다.
     *
     * <p>Kafka key에는 problemId 문자열을 사용하고,
     * value에는 Outbox에 저장된 JSON payload를 그대로 전달합니다.</p>
     *
     * @return ProblemPublished 이벤트용 KafkaTemplate
     */
    @Bean("problemPublishedKafkaTemplate")
    public KafkaTemplate<String, String>
    problemPublishedKafkaTemplate() {

        Map<String, Object> producerProperties =
                new HashMap<>();

        producerProperties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        producerProperties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        producerProperties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        /*
         * Kafka broker가 정상적으로 레코드를 복제했음을
         * 확인한 뒤 성공으로 판단합니다.
         */
        producerProperties.put(
                ProducerConfig.ACKS_CONFIG,
                "all"
        );

        /*
         * Producer 내부 재시도로 인한 중복 레코드를
         * 가능한 범위에서 방지합니다.
         *
         * Outbox Relay 전체의 전달 보장은 여전히
         * At-least-once입니다.
         */
        producerProperties.put(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                true
        );

        DefaultKafkaProducerFactory<String, String>
                producerFactory =
                new DefaultKafkaProducerFactory<>(
                        producerProperties
                );

        return new KafkaTemplate<>(
                producerFactory
        );
    }
}
