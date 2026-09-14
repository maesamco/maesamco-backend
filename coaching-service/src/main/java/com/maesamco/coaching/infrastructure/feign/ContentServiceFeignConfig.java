package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.security.hmac.HmacSigningFeignInterceptor;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * ContentServiceFeignClient 전용 설정 — Coaching이 Content에게 보내는 요청에 HMAC
 * 서명을 붙인다. JudgeServiceFeignConfig와 동일한 패턴(서비스 쌍마다 별도 키, 게이트웨이
 * 및 인증 보안 설계 6절) — 이번엔 Content 대상 키(HMAC_KEY_COACHING_TO_CONTENT)를 쓴다.
 *
 * 일부러 @Configuration을 안 붙인다(PR #127 심층 재검토, 2026-09-09) —
 * CoachingServiceApplication의 component scan 대상 패키지 안에 있어서 @Configuration을
 * 붙이면 이 클래스가 일반 빈으로도 등록되고, Spring Cloud OpenFeign의
 * NamedContextFactory#getInstances()가 ancestor(부모 컨텍스트) 빈까지 포함해서 조회하는
 * 바람에 JudgeServiceFeignClient까지 이 인터셉터를 상속해버린다(그 반대 방향도 마찬가지).
 * @FeignClient(configuration = ...)로 지정한 클래스는 @Configuration 없이도 각 Feign
 * Client 전용 child ApplicationContext에 등록되어 @Bean 메서드가 정상 동작한다.
 */
public class ContentServiceFeignConfig {

    @Bean
    public RequestInterceptor contentServiceHmacInterceptor(
            @Value("${spring.application.name}") String serviceName,
            @Value("${internal.hmac.outbound.content-service}") String secretKeyForContent
    ) {
        return new HmacSigningFeignInterceptor(serviceName, secretKeyForContent, InternalApiPrefix.INTERNAL_API_PREFIX);
    }

    /**
     * 모든 404를 "문제 없음"으로 단정하지 않는다(PR #127 심층 재검토, 2026-09-09) —
     * Content Service의 컨트롤러가 아직 배포 안 됐거나 경로/버전이 안 맞아도 404가 오는데,
     * 이런 경우까지 PROBLEM_NOT_FOUND로 오분류하면 CircuitBreakerIgnorableFailureConfig의
     * ignore 정책과 결합돼 실제 장애가 실패율에 전혀 안 잡힌다. Content Service도 이
     * 프로젝트 공통 에러 포맷(게이트웨이 및 인증 보안 설계 9절)을 쓰고, 컨트롤러 자체가
     * 없는 경우엔 NoResourceFoundException이 잡혀서 ENTITY_NOT_FOUND로 내려온다(content-
     * service GlobalExceptionHandler 확인) — 이 값과 다른 code가 와야만 진짜 "문제
     * 없음"으로 본다.
     */
    @Bean
    public ErrorDecoder contentServiceErrorDecoder() {
        return new ContentServiceErrorDecoder(ErrorCode.PROBLEM_NOT_FOUND, ErrorCode.FEIGN_CLIENT_ERROR);
    }
}
