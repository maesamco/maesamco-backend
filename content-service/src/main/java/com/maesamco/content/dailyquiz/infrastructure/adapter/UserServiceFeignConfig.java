package com.maesamco.content.dailyquiz.infrastructure.adapter;

import com.maesamco.content.global.security.hmac.HmacSigningFeignInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * User Service Feign 요청에 Content Service의 HMAC 서명을 추가하는 설정
 */
public class UserServiceFeignConfig {

    @Bean
    public RequestInterceptor userServiceHmacInterceptor(
            @Value("${spring.application.name}") String serviceName,
            @Value("${internal.hmac.outbound.user-service}") String secretKeyForUser
    ) {
        return new HmacSigningFeignInterceptor(serviceName, secretKeyForUser);
    }
}
