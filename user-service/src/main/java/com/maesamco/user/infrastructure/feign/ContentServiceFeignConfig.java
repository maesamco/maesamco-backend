package com.maesamco.user.infrastructure.feign;

import com.maesamco.user.global.security.hmac.HmacSigningFeignInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * ContentServiceFeignClient 전용 HMAC 서명 설정입니다.
 *
 * <p>{@code @Configuration}을 붙이지 않고 Feign Client의
 * 전용 자식 컨텍스트에서만 사용합니다.</p>
 */
public class ContentServiceFeignConfig {

    /**
     * User Service에서 Content Service로 전달하는 내부 요청에
     * 서비스 간 HMAC 서명을 추가합니다.
     */
    @Bean
    public RequestInterceptor contentServiceHmacInterceptor(
            @Value("${spring.application.name}")
            String serviceName,

            @Value("${internal.hmac.outbound.content-service}")
            String secretKeyForContent
    ) {
        return new HmacSigningFeignInterceptor(
                serviceName,
                secretKeyForContent
        );
    }
}
