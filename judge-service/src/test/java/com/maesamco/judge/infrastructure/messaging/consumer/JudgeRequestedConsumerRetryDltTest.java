package com.maesamco.judge.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maesamco.judge.application.facade.JudgeExecutionFacade;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.infrastructure.messaging.event.JudgeRequestedEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 이슈 #350 — "Facade가 예외를 다시 던지면 Kafka ErrorHandler가 재시도 후 DLT로 보낸다"는 가정을 실제 리스너 컨테이너로 확인한다.
 * Facade가 예외를 던지는 지점까지만 검증하는 단위 테스트로는, 예외가 정상 소비로 처리돼 메시지가 사라지는 회귀를 잡지 못한다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("JudgeRequested 소비 실패의 재시도/DLT")
class JudgeRequestedConsumerRetryDltTest {

    /** KafkaConsumerConfig의 FixedBackOff(1000ms, 3회 재시도) → 최초 1회 + 재시도 3회. */
    private static final int EXPECTED_DELIVERIES = 4;

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

    @MockitoBean
    private JudgeExecutionFacade judgeExecutionFacade;

    @Value("${spring.kafka.topic.judge-requested:judge-requested-events}")
    private String topic;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("Facade가 낙관적 락 충돌 예외를 던지면 설정된 횟수만큼 재전달된 뒤 DLT로 이동한다")
    void optimisticLockFailureIsRetriedThenSentToDlt() throws Exception {
        UUID submissionId = UUID.randomUUID();
        AtomicInteger deliveries = new AtomicInteger();
        Mockito.doAnswer(invocation -> {
            deliveries.incrementAndGet();
            throw new ObjectOptimisticLockingFailureException(Submission.class, submissionId);
        }).when(judgeExecutionFacade).execute(submissionId);

        JudgeRequestedEvent event = JudgeRequestedEvent.of(submissionId);
        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, submissionId.toString(), objectMapper.writeValueAsString(event)))
                    .get(5, TimeUnit.SECONDS);
        }

        try (KafkaConsumer<String, String> dltConsumer = createDltConsumer()) {
            dltConsumer.subscribe(List.of(topic + ".DLT", topic + "-dlt"));

            ConsumerRecord<String, String> received =
                    pollUntilFound(dltConsumer, submissionId.toString(), Duration.ofSeconds(30));

            assertThat(received).as("재시도가 끝난 뒤 DLT로 이동해야 한다(정상 소비로 사라지면 안 된다)").isNotNull();
            assertThat(deliveries.get()).isEqualTo(EXPECTED_DELIVERIES);
        }
    }

    private ConsumerRecord<String, String> pollUntilFound(
            KafkaConsumer<String, String> consumer, String fragment, Duration timeout
    ) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(fragment)) {
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
