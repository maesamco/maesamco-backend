package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.global.security.hmac.HmacSigningFeignInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JudgeServiceFeignClient 전용 설정 — Coaching이 Judge에게 보내는 요청에
 * HMAC 서명을 붙인다. 서비스 쌍마다 별도 키를 쓰므로(게이트웨이 및 인증 보안 설계 6절)
 * 이 설정은 Judge 대상 키(HMAC_KEY_COACHING_TO_JUDGE)만 사용한다 — 다른 대상(Content
 * 등)이 생기면 각자의 FeignConfig에 같은 패턴으로 추가한다.
 */
@Configuration
public class JudgeServiceFeignConfig {

    @Bean
    public RequestInterceptor judgeServiceHmacInterceptor(
            @Value("${spring.application.name}") String serviceName,
            @Value("${internal.hmac.outbound.judge-service}") String secretKeyForJudge
    ) {
        return new HmacSigningFeignInterceptor(serviceName, secretKeyForJudge);
    }
}
