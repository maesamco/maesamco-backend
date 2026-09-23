package com.maesamco.judge.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(ProblemPublishedConsumerDltTest.TestKafkaRetryConfiguration.class)
class ProblemPublishedConsumerDltTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.2"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TestRetryListener retryListener;

    @Value("${spring.kafka.topic.problem-published:problem-published-events}")
    private String topic;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void unsupportedEventVersionGoesStraightToDltWithoutRetry() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();

        ProblemPublishedEvent event = new ProblemPublishedEvent(
                eventId,
                "ProblemPublished",
                999,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "JAVA17",
                null,
                List.of(),
                1000,
                128,
                Instant.now()
        );

        String payload = objectMapper.writeValueAsString(event);

        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(
                    new ProducerRecord<>(
                            topic,
                            eventId.toString(),
                            payload
                    )
            ).get(5, TimeUnit.SECONDS);
        }

        // when
        try (KafkaConsumer<String, String> dltConsumer = createDltConsumer()) {
            dltConsumer.subscribe(List.of(
                    topic + ".DLT",
                    topic + "-dlt"
            ));

            ConsumerRecord<String, String> received =
                    pollUntilFound(
                            dltConsumer,
                            eventId.toString(),
                            Duration.ofSeconds(10)
                    );

            // then
            assertThat(received).isNotNull();
            assertThat(received.value()).contains(eventId.toString());

            // 지원하지 않는 eventVersion은 retry 없이 최초 실패 후 DLT로 이동해야 한다.
            assertThat(retryListener.getMaxDeliveryAttempt())
                    .isEqualTo(1);
        }
    }

    @TestConfiguration
    static class TestKafkaRetryConfiguration {

        @Bean
        @Primary
        TestRetryListener testRetryListener() {
            return new TestRetryListener();
        }
    }

    static class TestRetryListener implements RetryListener {

        private int maxDeliveryAttempt = 0;

        @Override
        public void failedDelivery(
                ConsumerRecord<?, ?> record,
                Exception ex,
                int deliveryAttempt
        ) {
            maxDeliveryAttempt =
                    Math.max(maxDeliveryAttempt, deliveryAttempt);
        }

        @Override
        public void recovered(
                ConsumerRecord<?, ?> record,
                Exception ex
        ) {
        }

        int getMaxDeliveryAttempt() {
            return maxDeliveryAttempt;
        }
    }

    private ConsumerRecord<String, String> pollUntilFound(
            KafkaConsumer<String, String> consumer, String expectedKeyOrValueFragment, Duration timeout
    ) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(expectedKeyOrValueFragment)) {
                    return record;
                }
            }
        }
        return null;
    }

    private KafkaProducer<String, String> createProducer() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        return new KafkaProducer<>(props);
    }

    private KafkaConsumer<String, String> createDltConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}