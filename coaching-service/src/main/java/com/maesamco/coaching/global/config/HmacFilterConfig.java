package com.maesamco.coaching.global.config;

import com.maesamco.coaching.global.security.hmac.HmacVerificationFilter;
import com.maesamco.coaching.global.security.hmac.InternalServiceKeyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HmacVerificationFilter를 /internal/v1/** 경로에만 걸어준다.
 * Gateway가 이 경로를 외부 라우팅 대상에서 이미 제외하지만(1차 방어선),
 * 이 필터는 그 격리가 깨지는 상황(설정 실수 등)을 대비한 2차 방어선이다
 * (게이트웨이 및 인증 보안 설계 6절).
 */
@Configuration
@EnableConfigurationProperties(InternalServiceKeyProperties.class)
public class HmacFilterConfig {

    @Bean
    public FilterRegistrationBean<HmacVerificationFilter> hmacVerificationFilterRegistration(
            InternalServiceKeyProperties keyProperties) {
        FilterRegistrationBean<HmacVerificationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new HmacVerificationFilter(keyProperties));
        registration.addUrlPatterns("/internal/v1/*");
        registration.setOrder(1); // Spring Security 필터 체인 이후, 컨트롤러 이전
        return registration;
    }
}
