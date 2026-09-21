package com.maesamco.content.infrastructure.dailyquiz.messaging;

import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventPublisherPort;
import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventPublishOutcomeUnknownException;
import org.apache.kafka.common.errors.RetriableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * DailyQuizCompleted Outbox payload를 Kafka로 발행하고 broker ACK를 확인
 */
@Component
public class KafkaDailyQuizCompletedEventPublisherAdapter
        implements DailyQuizCompletedEventPublisherPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final long publishTimeoutMillis;

    public KafkaDailyQuizCompletedEventPublisherAdapter(
            @Qualifier("outboxKafkaTemplate")
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${spring.kafka.topic.daily-quiz-completed:daily-quiz-completed-events}")
            String topic,
            @Value("${outbox.daily-quiz-completed.relay.publish-timeout-ms:5000}")
            long publishTimeoutMillis
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeoutMillis = publishTimeoutMillis;
    }

    @Override
    public void publish(UUID quizAttemptId, String payload) {
        String messageKey = quizAttemptId.toString();

        try {
            kafkaTemplate.send(topic, messageKey, payload)
                    .get(
                            publishTimeoutMillis,
                            TimeUnit.MILLISECONDS
                    );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new DailyQuizCompletedEventPublishOutcomeUnknownException(
                    "DailyQuizCompleted Kafka 발행 대기가 중단되어 "
                            + "실제 전달 여부를 확인할 수 없습니다. "
                            + "topic=" + topic + ", key=" + messageKey,
                    exception
            );
        } catch (TimeoutException exception) {
            throw new DailyQuizCompletedEventPublishOutcomeUnknownException(
                    "DailyQuizCompleted Kafka ACK 대기 시간을 초과해 "
                            + "실제 전달 여부를 확인할 수 없습니다. "
                            + "topic=" + topic + ", key=" + messageKey,
                    exception
            );
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RetriableException) {
                throw new DailyQuizCompletedEventPublishOutcomeUnknownException(
                        "DailyQuizCompleted Kafka 발행 중 재시도 가능한 오류가 발생해 "
                                + "실제 전달 여부를 확인할 수 없습니다. "
                                + "topic=" + topic + ", key=" + messageKey,
                        exception
                );
            }

            throw new IllegalStateException(
                    "DailyQuizCompleted Kafka 발행에 실패했습니다. "
                            + "topic=" + topic + ", key=" + messageKey,
                    exception.getCause()
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "DailyQuizCompleted Kafka 발행 요청에 실패했습니다. "
                            + "topic=" + topic + ", key=" + messageKey,
                    exception
            );
        }
    }
}
