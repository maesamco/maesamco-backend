package com.maesamco.content.global.config;

import com.maesamco.content.infrastructure.messaging.event.SubmissionJudgedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:29092";

    private KafkaConsumerConfig kafkaConsumerConfig;

    @BeforeEach
    void setUp() {
        kafkaConsumerConfig = new KafkaConsumerConfig();

        ReflectionTestUtils.setField(
                kafkaConsumerConfig,
                "bootstrapServers",
                BOOTSTRAP_SERVERS
        );
    }

    @Test
    @DisplayName("SubmissionJudged ConsumerFactory는 ErrorHandlingDeserializer와 이벤트 타입을 설정한다")
    void submissionJudgedConsumerFactory_configuresDeserializer() {
        // when
        ConsumerFactory<String, SubmissionJudgedEvent> consumerFactory =
                kafkaConsumerConfig.submissionJudgedConsumerFactory();

        // then
        assertThat(consumerFactory)
                .isInstanceOf(DefaultKafkaConsumerFactory.class);

        Map<String, Object> properties =
                consumerFactory.getConfigurationProperties();

        assertThat(properties.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG))
                .isEqualTo(BOOTSTRAP_SERVERS);

        assertThat(properties.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG))
                .isEqualTo(ErrorHandlingDeserializer.class);

        assertThat(properties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                .isEqualTo(ErrorHandlingDeserializer.class);

        assertThat(properties.get(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS))
                .isEqualTo(StringDeserializer.class);

        assertThat(properties.get(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS))
                .isEqualTo(JacksonJsonDeserializer.class);

        assertThat(properties.get(JacksonJsonDeserializer.TRUSTED_PACKAGES))
                .isEqualTo("com.maesamco.content.infrastructure.messaging.event");

        assertThat(properties.get(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE))
                .isEqualTo(SubmissionJudgedEvent.class.getName());

        assertThat(properties.get(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS))
                .isEqualTo(false);

        assertThat(properties.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG))
                .isEqualTo("earliest");

        assertThat(properties.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))
                .isEqualTo(false);
    }

    @Test
    @DisplayName("SubmissionJudged DLT KafkaTemplate은 bootstrap server와 String key serializer를 설정한다")
    void submissionJudgedDltKafkaTemplate_configuresProducer() {
        // when
        KafkaTemplate<Object, Object> kafkaTemplate =
                kafkaConsumerConfig.submissionJudgedDltKafkaTemplate();

        // then
        assertThat(kafkaTemplate)
                .isNotNull();

        assertThat(kafkaTemplate.getProducerFactory())
                .isInstanceOf(DefaultKafkaProducerFactory.class);

        Map<String, Object> properties =
                kafkaTemplate
                        .getProducerFactory()
                        .getConfigurationProperties();

        assertThat(properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
                .isEqualTo(BOOTSTRAP_SERVERS);

        assertThat(properties.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
                .isEqualTo(StringSerializer.class);
    }

    @Test
    @DisplayName("SubmissionJudged DLT Producer는 이벤트와 역직렬화 실패 원문을 처리할 수 있는 serializer를 사용한다")
    void submissionJudgedDltKafkaTemplate_usesDelegatingByTypeSerializer() {
        // given
        KafkaTemplate<Object, Object> kafkaTemplate =
                kafkaConsumerConfig.submissionJudgedDltKafkaTemplate();

        // when
        Object producerFactory =
                kafkaTemplate.getProducerFactory();

        // then
        assertThat(producerFactory)
                .isInstanceOf(DefaultKafkaProducerFactory.class);

        DefaultKafkaProducerFactory<?, ?> defaultProducerFactory =
                (DefaultKafkaProducerFactory<?, ?>) producerFactory;

        assertThat(defaultProducerFactory.getValueSerializer())
                .isInstanceOf(DelegatingByTypeSerializer.class);
    }

    @Test
    @DisplayName("SubmissionJudged ListenerContainerFactory에 전용 ConsumerFactory를 설정한다")
    void submissionJudgedKafkaListenerContainerFactory_setsConsumerFactory() {
        // given
        ConsumerFactory<String, SubmissionJudgedEvent> consumerFactory =
                kafkaConsumerConfig.submissionJudgedConsumerFactory();

        KafkaTemplate<Object, Object> dltKafkaTemplate =
                kafkaConsumerConfig.submissionJudgedDltKafkaTemplate();

        // when
        ConcurrentKafkaListenerContainerFactory<String, SubmissionJudgedEvent> factory =
                kafkaConsumerConfig.submissionJudgedKafkaListenerContainerFactory(
                        consumerFactory,
                        dltKafkaTemplate
                );

        // then
        assertThat(factory.getConsumerFactory())
                .isSameAs(consumerFactory);
    }

    @Test
    @DisplayName("SubmissionJudged Listener 오류 처리는 DefaultErrorHandler를 사용한다")
    void submissionJudgedKafkaListenerContainerFactory_setsDefaultErrorHandler() {
        // given
        ConsumerFactory<String, SubmissionJudgedEvent> consumerFactory =
                kafkaConsumerConfig.submissionJudgedConsumerFactory();

        KafkaTemplate<Object, Object> dltKafkaTemplate =
                kafkaConsumerConfig.submissionJudgedDltKafkaTemplate();

        // when
        ConcurrentKafkaListenerContainerFactory<String, SubmissionJudgedEvent> factory =
                kafkaConsumerConfig.submissionJudgedKafkaListenerContainerFactory(
                        consumerFactory,
                        dltKafkaTemplate
                );

        Object commonErrorHandler =
                ReflectionTestUtils.getField(
                        factory,
                        "commonErrorHandler"
                );

        // then
        assertThat(commonErrorHandler)
                .isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    @DisplayName("SubmissionJudged 처리 실패 시 1초 간격으로 3회 재시도한다")
    void submissionJudgedKafkaListenerContainerFactory_configuresRetryBackOff() {
        // given
        ConsumerFactory<String, SubmissionJudgedEvent> consumerFactory =
                kafkaConsumerConfig.submissionJudgedConsumerFactory();

        KafkaTemplate<Object, Object> dltKafkaTemplate =
                kafkaConsumerConfig.submissionJudgedDltKafkaTemplate();

        ConcurrentKafkaListenerContainerFactory<String, SubmissionJudgedEvent> factory =
                kafkaConsumerConfig.submissionJudgedKafkaListenerContainerFactory(
                        consumerFactory,
                        dltKafkaTemplate
                );

        DefaultErrorHandler errorHandler =
                (DefaultErrorHandler) ReflectionTestUtils.getField(
                        factory,
                        "commonErrorHandler"
                );

        // when
        Object failureTracker =
                ReflectionTestUtils.getField(
                        errorHandler,
                        "failureTracker"
                );

        FixedBackOff backOff =
                (FixedBackOff) ReflectionTestUtils.getField(
                        failureTracker,
                        "backOff"
                );

        // then
        assertThat(backOff)
                .isNotNull();

        assertThat(backOff.getInterval())
                .isEqualTo(1000L);

        assertThat(backOff.getMaxAttempts())
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("SubmissionJudged 재시도 소진 후 DeadLetterPublishingRecoverer로 복구한다")
    void submissionJudgedKafkaListenerContainerFactory_configuresDltRecoverer() {
        // given
        ConsumerFactory<String, SubmissionJudgedEvent> consumerFactory =
                kafkaConsumerConfig.submissionJudgedConsumerFactory();

        KafkaTemplate<Object, Object> dltKafkaTemplate =
                kafkaConsumerConfig.submissionJudgedDltKafkaTemplate();

        ConcurrentKafkaListenerContainerFactory<String, SubmissionJudgedEvent> factory =
                kafkaConsumerConfig.submissionJudgedKafkaListenerContainerFactory(
                        consumerFactory,
                        dltKafkaTemplate
                );

        DefaultErrorHandler errorHandler =
                (DefaultErrorHandler) ReflectionTestUtils.getField(
                        factory,
                        "commonErrorHandler"
                );

        // when
        Object failureTracker =
                ReflectionTestUtils.getField(
                        errorHandler,
                        "failureTracker"
                );

        Object recoverer =
                ReflectionTestUtils.getField(
                        failureTracker,
                        "recoverer"
                );

        // then
        assertThat(recoverer)
                .isInstanceOf(DeadLetterPublishingRecoverer.class);
    }
}