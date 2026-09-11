package com.maesamco.judge.application.port;

/**
 * Outbox Relay가 이벤트를 발행할 때 쓰는 포트
 */
public interface EventPublisherPort {

    /**
     * @param topic   발행 대상 토픽
     * @param key     파티셔닝 키 (aggregateId 문자열)
     * @param payload 이미 직렬화된 JSON 문자열 페이로드
     */
    void publish(String topic, String key, String payload);
}
