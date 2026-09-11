package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.security.hmac.HmacSigningFeignInterceptor;
import com.maesamco.coaching.global.security.hmac.InternalCallHeaders;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
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
        return new HmacSigningFeignInterceptor(serviceName, secretKeyForJudge, InternalCallHeaders.INTERNAL_API_PREFIX);
    }

    /**
     * 모든 404를 "제출 없음"으로 단정하지 않는다(PR #127 리뷰, 용현님 지적 —
     * ContentServiceErrorDecoder와 동일한 결함이 여기 남아있었음) — Judge Service의
     * 컨트롤러가 아직 배포 안 됐거나 경로/버전이 안 맞아도 404가 오는데, 이런 경우까지
     * SUBMISSION_NOT_FOUND로 오분류하면 CircuitBreakerIgnorableFailureConfig의 ignore
     * 정책과 결합돼 실제 장애가 실패율에 전혀 안 잡힌다. Judge Service도 이 프로젝트
     * 공통 에러 포맷(게이트웨이 및 인증 보안 설계 9절)을 쓰고, 컨트롤러 자체가 없는
     * 경우엔 NoResourceFoundException이 잡혀서 ENTITY_NOT_FOUND로 내려온다(judge-
     * service GlobalExceptionHandler 확인) — 이 값과 다른 code가 와야만 진짜 "제출
     * 없음"으로 본다.
     */
    @Bean
    public ErrorDecoder judgeServiceErrorDecoder() {
        return new JudgeServiceErrorDecoder(ErrorCode.SUBMISSION_NOT_FOUND, ErrorCode.FEIGN_CLIENT_ERROR);
    }
}
