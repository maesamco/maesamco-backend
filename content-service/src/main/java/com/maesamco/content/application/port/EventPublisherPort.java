package com.maesamco.content.application.port;

/**
 * Outbox Relay가 이벤트를 외부 메시지 브로커로 발행할 때 사용하는 포트입니다.
 * Application 계층이 Kafka 구현에 직접 의존하지 않도록 분리합니다.
 */
public interface EventPublisherPort {

    void publish(String topic, String key, String payload);
}