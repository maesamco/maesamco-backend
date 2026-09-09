package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.global.security.hmac.HmacSigningFeignInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * JudgeServiceFeignClient 전용 설정 — Coaching이 Judge에게 보내는 요청에
 * HMAC 서명을 붙인다. 서비스 쌍마다 별도 키를 쓰므로(게이트웨이 및 인증 보안 설계 6절)
 * 이 설정은 Judge 대상 키(HMAC_KEY_COACHING_TO_JUDGE)만 사용한다 — 다른 대상(Content
 * 등)이 생기면 각자의 FeignConfig에 같은 패턴으로 추가한다.
 *
 * 일부러 @Configuration을 안 붙인다(PR #127 심층 재검토, 2026-09-09) — component scan에
 * 걸리면 이 인터셉터가 parent context에도 등록되고, Spring Cloud OpenFeign의
 * NamedContextFactory#getInstances()가 ancestor 빈까지 포함해서 조회하는 바람에
 * ContentServiceFeignClient까지 이 인터셉터를 상속해버린다. @FeignClient(configuration
 * = ...)로 지정하면 @Configuration 없이도 각 Feign Client 전용 child
 * ApplicationContext에서 @Bean 메서드가 정상 동작한다(ContentServiceFeignConfig 참고).
 */
public class JudgeServiceFeignConfig {

    @Bean
    public RequestInterceptor judgeServiceHmacInterceptor(
            @Value("${spring.application.name}") String serviceName,
            @Value("${internal.hmac.outbound.judge-service}") String secretKeyForJudge
    ) {
        return new HmacSigningFeignInterceptor(serviceName, secretKeyForJudge);
    }
}
