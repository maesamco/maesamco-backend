package com.maesamco.coaching.global.security.hmac;

import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 content-service를 띄운 통합 검증(이슈 #126)에서 재현한 버그(이슈 #161)의
 * 회귀 방지용 — basePath가 왜 필요한지는 HmacSigningFeignInterceptor의 클래스
 * Javadoc 참고.
 */
class HmacSigningFeignInterceptorTest {

    private static final String SERVICE_NAME = "coaching-service";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String BASE_PATH = "/internal/v1";

    @Test
    void 서명_대상_경로에_basePath가_포함된다() {
        RequestTemplate template = new RequestTemplate();
        template.method(Request.HttpMethod.GET);
        template.uri("/problems/11111111-1111-1111-1111-111111111111");

        new HmacSigningFeignInterceptor(SERVICE_NAME, SECRET_KEY, BASE_PATH).apply(template);

        String nonce = firstHeader(template, InternalCallHeaders.NONCE);
        long timestamp = Long.parseLong(firstHeader(template, InternalCallHeaders.TIMESTAMP));
        String signature = firstHeader(template, InternalCallHeaders.SIGNATURE);
        String bodyHash = HmacSignatureUtil.hashBody(template.body());

        boolean validAgainstFullPath = HmacSignatureUtil.verify(
                SERVICE_NAME, "GET", BASE_PATH + "/problems/11111111-1111-1111-1111-111111111111",
                "", bodyHash, nonce, timestamp, SECRET_KEY, signature);

        assertThat(validAgainstFullPath).isTrue();
    }

    @Test
    void basePath가_빠진_경로로는_검증에_실패한다() {
        RequestTemplate template = new RequestTemplate();
        template.method(Request.HttpMethod.GET);
        template.uri("/problems/11111111-1111-1111-1111-111111111111");

        new HmacSigningFeignInterceptor(SERVICE_NAME, SECRET_KEY, BASE_PATH).apply(template);

        String nonce = firstHeader(template, InternalCallHeaders.NONCE);
        long timestamp = Long.parseLong(firstHeader(template, InternalCallHeaders.TIMESTAMP));
        String signature = firstHeader(template, InternalCallHeaders.SIGNATURE);
        String bodyHash = HmacSignatureUtil.hashBody(template.body());

        // 수정 전 버그를 그대로 재현 — basePath 없이 검증하면(수신측이 실제로 보는
        // 전체 경로가 아니라 method-level 경로만으로 검증하면) 실패해야 한다.
        boolean validWithoutBasePath = HmacSignatureUtil.verify(
                SERVICE_NAME, "GET", "/problems/11111111-1111-1111-1111-111111111111",
                "", bodyHash, nonce, timestamp, SECRET_KEY, signature);

        assertThat(validWithoutBasePath).isFalse();
    }

    private String firstHeader(RequestTemplate template, String name) {
        Map<String, java.util.Collection<String>> headers = template.headers();
        return headers.get(name).iterator().next();
    }
}
