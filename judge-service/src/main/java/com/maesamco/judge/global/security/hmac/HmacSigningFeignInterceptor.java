package com.maesamco.judge.global.security.hmac;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

/**
 * /internal/v1/** 를 호출하는 모든 Feign Client에 등록한다.
 * 이 서비스가 "발신자"일 때, 상대(수신) 서비스와 공유하는 키로 서명한다.
 *
 * 주의: InternalServiceKeyProperties는 "내가 받을 때 상대를 검증하는 키 맵"이고,
 * 이 인터셉터가 쓰는 키는 "내가 보낼 때 상대가 나를 검증할 키"다.
 * 두 방향의 키가 반드시 같은 값이어야 하므로(대칭키), 서비스 쌍마다
 * "누가 발신자일 때 어떤 키를 쓰는지"를 인프라 설계도/설정에 명확히 표로 남겨둘 것.
 */
public class HmacSigningFeignInterceptor implements RequestInterceptor {

    private final String serviceName;
    private final String secretKeyForTarget;

    public HmacSigningFeignInterceptor(@Value("${spring.application.name}") String serviceName,
                                       String secretKeyForTarget) {
        this.serviceName = serviceName;
        this.secretKeyForTarget = secretKeyForTarget;
    }

    @Override
    public void apply(RequestTemplate template) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String method = template.method();
        String url = template.url();
        String path = stripQuery(url);
        String normalizedQuery = HmacSignatureUtil.normalizeQuery(extractQuery(url));
        String bodyHash = HmacSignatureUtil.hashBody(template.body());

        String signature = HmacSignatureUtil.sign(
                serviceName, method, path, normalizedQuery, bodyHash, nonce, timestamp, secretKeyForTarget);

        template.header(InternalCallHeaders.SERVICE, serviceName);
        template.header(InternalCallHeaders.TIMESTAMP, String.valueOf(timestamp));
        template.header(InternalCallHeaders.NONCE, nonce);
        template.header(InternalCallHeaders.SIGNATURE, signature);
    }

    /**
     * RequestTemplate.url()은 쿼리스트링까지 포함할 수 있어, 검증 측(서버가 보는
     * request.getRequestURI())과 동일한 기준으로 맞추기 위해 경로와 쿼리를 분리한다.
     */
    private String stripQuery(String url) {
        int idx = url.indexOf('?');
        return idx == -1 ? url : url.substring(0, idx);
    }

    private String extractQuery(String url) {
        int idx = url.indexOf('?');
        return idx == -1 ? "" : url.substring(idx + 1);
    }
}