package com.maesamco.content.global.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:29092";

    private KafkaProducerConfig kafkaProducerConfig;

    @BeforeEach
    void setUp() {
        kafkaProducerConfig = new KafkaProducerConfig();

        ReflectionTestUtils.setField(
                kafkaProducerConfig,
                "bootstrapServers",
                BOOTSTRAP_SERVERS
        );
    }

    @Test
    @DisplayName("Outbox ProducerFactory를 생성한다")
    void outboxProducerFactory_createsProducerFactory() {
        // when
        ProducerFactory<String, String> producerFactory =
                kafkaProducerConfig.outboxProducerFactory();

        // then
        assertThat(producerFactory)
                .isInstanceOf(DefaultKafkaProducerFactory.class);
    }

    @Test
    @DisplayName("Outbox ProducerFactory에 Kafka bootstrap server를 설정한다")
    void outboxProducerFactory_configuresBootstrapServers() {
        // given
        ProducerFactory<String, String> producerFactory =
                kafkaProducerConfig.outboxProducerFactory();

        // when
        Map<String, Object> properties =
                producerFactory.getConfigurationProperties();

        // then
        assertThat(
                properties.get(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG
                )
        ).isEqualTo(BOOTSTRAP_SERVERS);
    }

    @Test
    @DisplayName("Outbox ProducerFactory는 key와 value에 StringSerializer를 사용한다")
    void outboxProducerFactory_configuresStringSerializers() {
        // given
        ProducerFactory<String, String> producerFactory =
                kafkaProducerConfig.outboxProducerFactory();

        // when
        Map<String, Object> properties =
                producerFactory.getConfigurationProperties();

        // then
        assertThat(
                properties.get(
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
                )
        ).isEqualTo(StringSerializer.class);

        assertThat(
                properties.get(
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
                )
        ).isEqualTo(StringSerializer.class);
    }

    @Test
    @DisplayName("Outbox ProducerFactory는 모든 replica 확인과 idempotence를 활성화한다")
    void outboxProducerFactory_configuresReliabilityOptions() {
        // given
        ProducerFactory<String, String> producerFactory =
                kafkaProducerConfig.outboxProducerFactory();

        // when
        Map<String, Object> properties =
                producerFactory.getConfigurationProperties();

        // then
        assertThat(
                properties.get(
                        ProducerConfig.ACKS_CONFIG
                )
        ).isEqualTo("all");

        assertThat(
                properties.get(
                        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG
                )
        ).isEqualTo(true);
    }

    @Test
    @DisplayName("Outbox KafkaTemplate은 Outbox ProducerFactory를 사용한다")
    void outboxKafkaTemplate_usesOutboxProducerFactory() {
        // given
        ProducerFactory<String, String> producerFactory =
                kafkaProducerConfig.outboxProducerFactory();

        // when
        KafkaTemplate<String, String> kafkaTemplate =
                kafkaProducerConfig.outboxKafkaTemplate(
                        producerFactory
                );

        // then
        assertThat(kafkaTemplate)
                .isNotNull();

        assertThat(kafkaTemplate.getProducerFactory())
                .isSameAs(producerFactory);
    }
}