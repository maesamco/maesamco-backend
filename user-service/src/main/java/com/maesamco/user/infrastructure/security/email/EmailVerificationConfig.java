package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.service.EmailVerificationPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이메일 인증에 필요한 설정과 정책을 등록합니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class EmailVerificationConfig {

    /**
     * 외부 설정값을 이메일 인증 정책으로 변환합니다.
     */
    @Bean
    public EmailVerificationPolicy emailVerificationPolicy(
            EmailVerificationProperties properties
    ) {
        return properties.toPolicy();
    }
}
