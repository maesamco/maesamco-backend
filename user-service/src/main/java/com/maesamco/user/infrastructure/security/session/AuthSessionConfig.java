package com.maesamco.user.infrastructure.security.session;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 인증 세션과 Refresh Token Rotation에 필요한 설정을 등록합니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthSessionProperties.class)
public class AuthSessionConfig {
}
