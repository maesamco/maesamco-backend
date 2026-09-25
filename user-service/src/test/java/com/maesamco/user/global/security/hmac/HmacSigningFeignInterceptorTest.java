package com.maesamco.user.global.security.hmac;

import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관심 개념 저장(PUT /users/me/interests)이 개발 서버에서 항상 503(CONTENT_SERVICE_UNAVAILABLE)이던 버그의
 * 회귀 방지용이다. Content Service가 401(서명 불일치)을 돌려줬는데, 원인은 서명 대상 경로에 {@code @FeignClient(path)}
 * prefix가 빠진 것이었다 — basePath가 왜 필요한지는 HmacSigningFeignInterceptor의 클래스 Javadoc 참고.
 */
class HmacSigningFeignInterceptorTest {

    private static final String SERVICE_NAME = "user-service";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String BASE_PATH = "/internal/v1";

    @Test
    @DisplayName("서명 대상 경로에 basePath가 포함된다 — 수신 측이 보는 전체 경로로 검증에 성공한다")
    void signsTheFullPathIncludingBasePath() {
        RequestTemplate template = postValidateTemplate("{\"conceptIds\":[\"11111111-1111-1111-1111-111111111111\"]}");

        new HmacSigningFeignInterceptor(SERVICE_NAME, SECRET_KEY, BASE_PATH).apply(template);

        boolean validAgainstFullPath = verify(template, BASE_PATH + "/concepts/validate");

        assertThat(validAgainstFullPath).isTrue();
    }

    @Test
    @DisplayName("basePath가 빠진 경로로는 검증에 실패한다 — 수정 전 버그를 그대로 재현")
    void failsWhenVerifiedWithoutBasePath() {
        RequestTemplate template = postValidateTemplate("{\"conceptIds\":[]}");

        new HmacSigningFeignInterceptor(SERVICE_NAME, SECRET_KEY, BASE_PATH).apply(template);

        assertThat(verify(template, "/concepts/validate")).isFalse();
    }

    @Test
    @DisplayName("요청 본문도 서명에 포함된다 — 본문이 바뀌면 검증에 실패한다")
    void signatureCoversRequestBody() {
        RequestTemplate template = postValidateTemplate("{\"conceptIds\":[\"a\"]}");

        new HmacSigningFeignInterceptor(SERVICE_NAME, SECRET_KEY, BASE_PATH).apply(template);

        String nonce = firstHeader(template, InternalCallHeaders.NONCE);
        long timestamp = Long.parseLong(firstHeader(template, InternalCallHeaders.TIMESTAMP));
        String signature = firstHeader(template, InternalCallHeaders.SIGNATURE);
        String tamperedBodyHash = HmacSignatureUtil.hashBody("{\"conceptIds\":[\"b\"]}".getBytes(StandardCharsets.UTF_8));

        assertThat(HmacSignatureUtil.verify(SERVICE_NAME, "POST", BASE_PATH + "/concepts/validate", "",
                tamperedBodyHash, nonce, timestamp, SECRET_KEY, signature)).isFalse();
    }

    private RequestTemplate postValidateTemplate(String jsonBody) {
        RequestTemplate template = new RequestTemplate();
        template.method(Request.HttpMethod.POST);
        template.uri("/concepts/validate");
        template.body(jsonBody.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        return template;
    }

    private boolean verify(RequestTemplate template, String path) {
        String nonce = firstHeader(template, InternalCallHeaders.NONCE);
        long timestamp = Long.parseLong(firstHeader(template, InternalCallHeaders.TIMESTAMP));
        String signature = firstHeader(template, InternalCallHeaders.SIGNATURE);
        String bodyHash = HmacSignatureUtil.hashBody(template.body());
        return HmacSignatureUtil.verify(SERVICE_NAME, "POST", path, "", bodyHash, nonce, timestamp, SECRET_KEY, signature);
    }

    private String firstHeader(RequestTemplate template, String name) {
        Map<String, java.util.Collection<String>> headers = template.headers();
        return headers.get(name).iterator().next();
    }
}
