package com.maesamco.judge.global.config;

import com.maesamco.judge.global.security.hmac.InternalCallerAuthorizationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code @AllowedInternalCallers}를 실제로 강제하는 인터셉터를 /internal/v1/**
 * 경로에만 등록한다(이슈 #138).
 */
@Configuration
public class InternalCallerAuthorizationConfig implements WebMvcConfigurer {

    @Bean
    public InternalCallerAuthorizationInterceptor internalCallerAuthorizationInterceptor() {
        return new InternalCallerAuthorizationInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalCallerAuthorizationInterceptor())
                .addPathPatterns("/internal/v1/**");
    }
}