package com.maesamco.content.application.dailyquiz.port;

/**
 * DailyQuizCompleted Kafka 발행의 실제 전달 여부를 확인하지 못한 경우 발생합니다.
 *
 * ACK 대기 시간 초과나 대기 중 인터럽트처럼 Producer 호출 결과를 확인하지 못한 경우,
 * 이벤트가 이미 Kafka에 전달되었을 가능성이 있습니다. Relay는 이 예외를 확정 발행 실패와
 * 구분해 이벤트를 FAILED로 종료하지 않아야 합니다.
 */
public class DailyQuizCompletedEventPublishOutcomeUnknownException extends RuntimeException {

    public DailyQuizCompletedEventPublishOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
