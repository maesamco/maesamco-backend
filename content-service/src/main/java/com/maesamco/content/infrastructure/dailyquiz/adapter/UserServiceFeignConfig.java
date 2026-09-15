package com.maesamco.content.infrastructure.dailyquiz.adapter;

import com.maesamco.content.global.security.hmac.HmacSigningFeignInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * User Service Feign 요청에 Content Service의 HMAC 서명을 추가하는 설정
 *
 * ⚠️ 이슈 #163 — UserServiceFeignClient가 {@code @FeignClient(path = InternalApiPrefix.
 * INTERNAL_API_PREFIX)}로 선언되어 있어, 서명 시 이 prefix를 반드시 basePath로 넘겨야
 * 한다. 리터럴 문자열 대신 같은 상수를 참조해서, 둘이 따로 바뀌다 어긋나는 걸 막는다
 * (PR #162 설계 그대로 적용).
 */
public class UserServiceFeignConfig {

    @Bean
    public RequestInterceptor userServiceHmacInterceptor(
            @Value("${spring.application.name}") String serviceName,
            @Value("${internal.hmac.outbound.user-service}") String secretKeyForUser
    ) {
        return new HmacSigningFeignInterceptor(serviceName, secretKeyForUser, InternalApiPrefix.INTERNAL_API_PREFIX);
    }
}