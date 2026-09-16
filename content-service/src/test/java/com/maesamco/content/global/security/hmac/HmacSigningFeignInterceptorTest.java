package com.maesamco.content.global.security.hmac;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * basePath가 실제로 서명에 반영되는지, 안 반영하면 검증이 어떻게 어긋나는지 확인한다
 * (이슈 #163, PR #162의 HmacSigningFeignInterceptorTest와 동일한 목적 — 회귀 방지).
 *
 * 네트워크·Redis 등 외부 의존성 없이 순수하게 서명 계산 로직만 검증한다.
 *
 * ⚠️ template.target(...)을 호출하지 않는다 — RequestInterceptor.apply()가 실행되는
 * 시점은 실제 Feign 흐름에서 Target이 아직 적용되기 "전"이라(이슈 #163의 근본 원인이
 * 바로 이 순서), template.target(...)을 테스트에서 호출하면 template.url()에 호스트가
 * 섞여 들어가 실제 운영 흐름과 다른 상태를 검증하게 된다. template.uri(path)만으로
 * 인터셉터가 실제로 마주치는 상태를 재현한다.
 */
class HmacSigningFeignInterceptorTest {

    private static final String SERVICE_NAME = "content-service";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String BASE_PATH = "/internal/v1";

    @Test
    void basePath를_넘기면_서명_대상_path에_실제로_prefix가_붙는다() {
        HmacSigningFeignInterceptor interceptor =
                new HmacSigningFeignInterceptor(SERVICE_NAME, SECRET_KEY, BASE_PATH);

        RequestTemplate template = new RequestTemplate();
        template.method(feign.Request.HttpMethod.GET);
        // Feign이 @FeignClient(path=...) prefix를 합치기 "전" 시점의 값 — 실제 버그
        // 상황과 동일하게, 인터셉터가 보는 template.url()에는 prefix가 없다.
        template.uri("/users/quiz-targets");

        interceptor.apply(template);

        String signature = template.headers().get(InternalCallHeaders.SIGNATURE).iterator().next();

        // basePath 없이(기존 2-인자 생성자) 계산한 서명과는 달라야 한다 —
        // 즉 basePath가 실제로 서명 계산에 반영됐다는 뜻.
        HmacSigningFeignInterceptor interceptorWithoutBasePath =
                new HmacSigningFeignInterceptor(SERVICE_NAME, SECRET_KEY, "");
        RequestTemplate templateWithoutBasePath = new RequestTemplate();
        templateWithoutBasePath.method(feign.Request.HttpMethod.GET);
        templateWithoutBasePath.uri("/users/quiz-targets");
        interceptorWithoutBasePath.apply(templateWithoutBasePath);
        String signatureWithoutBasePath =
                templateWithoutBasePath.headers().get(InternalCallHeaders.SIGNATURE).iterator().next();

        assertThat(signature).isNotEqualTo(signatureWithoutBasePath);
    }

    @Test
    void 수신측이_실제로_받는_전체_경로_기준으로_검증하면_basePath_반영된_서명만_일치한다() {
        // 수신측(HmacVerificationFilter)이 보는 값은 항상 전체 경로
        // (request.getRequestURI(), prefix 포함)다 — 검증 시 이 값을 그대로 쓴다.
        String actualReceivedPath = BASE_PATH + "/users/quiz-targets";

        HmacSigningFeignInterceptor interceptor =
                new HmacSigningFeignInterceptor(SERVICE_NAME, SECRET_KEY, BASE_PATH);
        RequestTemplate template = new RequestTemplate();
        template.method(feign.Request.HttpMethod.GET);
        template.uri("/users/quiz-targets");
        interceptor.apply(template);

        String timestamp = template.headers().get(InternalCallHeaders.TIMESTAMP).iterator().next();
        String nonce = template.headers().get(InternalCallHeaders.NONCE).iterator().next();
        String signature = template.headers().get(InternalCallHeaders.SIGNATURE).iterator().next();
        String bodyHash = HmacSignatureUtil.hashBody(null);

        boolean valid = HmacSignatureUtil.verify(
                SERVICE_NAME, "GET", actualReceivedPath, "", bodyHash, nonce,
                Long.parseLong(timestamp), SECRET_KEY, signature);

        assertThat(valid).isTrue();
    }

    // ⚠️ P5 리뷰(PR #164) — 위 테스트가 "고쳐졌으면 성공한다"만 증명하고 있어서,
    // PR #162 스타일에 맞춰 "고쳐지지 않았다면(basePath 없이 서명했다면) 반드시
    // 실패한다"는 것도 명시적인 음성 케이스로 증명한다.
    @Test
    void basePath_없이_서명하면_실제_수신_경로_기준_검증에서_반드시_실패한다() {
        String actualReceivedPath = BASE_PATH + "/users/quiz-targets";

        // 2-인자 생성자가 제거됐으므로, basePath를 "" 로 명시해서 예전 버그 상황
        // (basePath를 안 넘긴 것과 동일한 결과)을 재현한다.
        HmacSigningFeignInterceptor interceptorWithoutBasePath =
                new HmacSigningFeignInterceptor(SERVICE_NAME, SECRET_KEY, "");
        RequestTemplate template = new RequestTemplate();
        template.method(feign.Request.HttpMethod.GET);
        template.uri("/users/quiz-targets");
        interceptorWithoutBasePath.apply(template);

        String timestamp = template.headers().get(InternalCallHeaders.TIMESTAMP).iterator().next();
        String nonce = template.headers().get(InternalCallHeaders.NONCE).iterator().next();
        String signature = template.headers().get(InternalCallHeaders.SIGNATURE).iterator().next();
        String bodyHash = HmacSignatureUtil.hashBody(null);

        boolean valid = HmacSignatureUtil.verify(
                SERVICE_NAME, "GET", actualReceivedPath, "", bodyHash, nonce,
                Long.parseLong(timestamp), SECRET_KEY, signature);

        assertThat(valid).isFalse();
    }
}