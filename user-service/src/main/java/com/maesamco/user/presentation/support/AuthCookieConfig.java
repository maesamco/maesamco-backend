package com.maesamco.user.presentation.support;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 인증 Cookie 생성에 필요한 설정과 구성 요소를 등록합니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthCookieProperties.class)
public class AuthCookieConfig {

    @Bean
    public RefreshTokenCookieFactory refreshTokenCookieFactory(
            AuthCookieProperties properties
    ) {
        return new RefreshTokenCookieFactory(
                properties
        );
    }
}
