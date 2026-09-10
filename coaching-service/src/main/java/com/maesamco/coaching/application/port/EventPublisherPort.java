package com.maesamco.coaching.application.port;

/**
 * Outbox Relay가 이벤트를 발행할 때 쓰는 포트 — Judge Service의 동일한 포트(이슈 #63)와
 * 같은 설계다.
 */
public interface EventPublisherPort {

    /**
     * @param topic   발행 대상 토픽
     * @param key     파티셔닝 키 (aggregateId 문자열)
     * @param payload 이미 직렬화된 JSON 문자열 페이로드
     */
    void publish(String topic, String key, String payload);
}
