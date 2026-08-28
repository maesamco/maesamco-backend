package com.maesamco.content.global.security.hmac;

import feign.RequestInterceptor;
import feign.RequestTemplate;
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
        String signature = HmacSignatureUtil.sign(serviceName, timestamp, secretKeyForTarget);

        template.header(InternalCallHeaders.SERVICE, serviceName);
        template.header(InternalCallHeaders.TIMESTAMP, String.valueOf(timestamp));
        template.header(InternalCallHeaders.SIGNATURE, signature);
    }
}
