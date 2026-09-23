package com.maesamco.user.infrastructure.security.social;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 소셜 회원가입 Token 정책 설정을 등록합니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
        SocialSignupProperties.class
)
public class SocialSignupConfig {
}
