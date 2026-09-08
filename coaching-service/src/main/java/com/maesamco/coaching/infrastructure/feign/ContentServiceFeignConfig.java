package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.global.security.hmac.HmacSigningFeignInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ContentServiceFeignClient 전용 설정 — Coaching이 Content에게 보내는 요청에 HMAC
 * 서명을 붙인다. JudgeServiceFeignConfig와 동일한 패턴(서비스 쌍마다 별도 키, 게이트웨이
 * 및 인증 보안 설계 6절) — 이번엔 Content 대상 키(HMAC_KEY_COACHING_TO_CONTENT)를 쓴다.
 */
@Configuration
public class ContentServiceFeignConfig {

    @Bean
    public RequestInterceptor contentServiceHmacInterceptor(
            @Value("${spring.application.name}") String serviceName,
            @Value("${internal.hmac.outbound.content-service}") String secretKeyForContent
    ) {
        return new HmacSigningFeignInterceptor(serviceName, secretKeyForContent);
    }
}
