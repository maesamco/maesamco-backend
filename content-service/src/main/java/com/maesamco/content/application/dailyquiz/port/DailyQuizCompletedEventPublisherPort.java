package com.maesamco.content.application.dailyquiz.port;

import java.util.UUID;

/**
 * DailyQuizCompleted Outbox 이벤트를 외부 메시지 브로커로 발행하는 포트
 */
public interface DailyQuizCompletedEventPublisherPort {

    void publish(
            //Kafka message key로 사용할 Daily Quiz Attempt ID
            UUID quizAttemptId,
            // payload Outbox에 저장된 DailyQuizCompleted JSON payload
            String payload
    );
}
