package com.maesamco.judge.global.security.hmac;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 서비스 쌍별 HMAC 키 설정.
 * 4개 서비스가 단일 키를 공유하면 검증 가능한 쪽이 곧 위조도 가능해지므로
 * 반드시 서비스 쌍마다 별도 키를 둔다(게이트웨이 및 인증 보안 설계 6절).
 *
 * application.yml 예:
 *   internal:
 *     hmac:
 *       keys:
 *         content-service: ${HMAC_KEY_CONTENT}   # content-service가 나에게 보낼 때 쓰는 키
 *         coaching-service: ${HMAC_KEY_COACHING}
 *
 * 즉 이 서비스가 "수신자"일 때, 발신자(caller) 서비스 이름을 키로 각 발신자 전용 비밀키를 조회한다.
 */
@ConfigurationProperties(prefix = "internal.hmac")
public record InternalServiceKeyProperties(Map<String, String> keys) {

    public String keyFor(String callerServiceName) {
        String key = keys.get(callerServiceName);
        if (key == null) {
            throw new IllegalStateException("등록되지 않은 내부 호출 서비스: " + callerServiceName);
        }
        return key;
    }
}
