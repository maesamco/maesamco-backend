package com.maesamco.content.application.port;

/**
 * 이벤트 발행 결과를 확정할 수 없는 경우 사용하는 예외입니다.
 * 실제 전달됐을 가능성이 있으므로 일반 발행 실패와 구분해 처리합니다.
 */
public class EventPublishOutcomeUnknownException extends RuntimeException {

    public EventPublishOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}