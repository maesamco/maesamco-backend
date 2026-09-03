package com.maesamco.user.infrastructure.security.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 이메일 암호화와 조회 해시에 필요한 설정을 등록합니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailSecurityProperties.class)
public class EmailSecurityConfig {
}
