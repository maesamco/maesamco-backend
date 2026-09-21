package com.maesamco.content.global.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Content Service의 Outbox Relay가 사용할 Kafka Producer를 설정합니다.
 * 이벤트 payload는 Outbox에 저장된 JSON 문자열을 그대로 발행합니다.
 */
@Configuration
public class KafkaProducerConfig {

    private final String bootstrapServers;

    public KafkaProducerConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
            String bootstrapServers
    ) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public ProducerFactory<String, String> outboxProducerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Broker의 정상 복제를 확인하고 Producer 내부 재시도로 인한 중복 발행을 방지합니다.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Content Service Outbox 이벤트가 공통으로 사용하는 KafkaTemplate입니다.
     * 기존 ProblemPublished Bean 이름은 호환성을 위해 함께 유지합니다.
     */
    @Bean({
            "outboxKafkaTemplate",
            "problemPublishedKafkaTemplate"
    })
    public KafkaTemplate<String, String> outboxKafkaTemplate(
            @Qualifier("outboxProducerFactory")
            ProducerFactory<String, String> outboxProducerFactory
    ) {
        return new KafkaTemplate<>(
                outboxProducerFactory
        );
    }
}