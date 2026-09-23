package com.maesamco.content.global.config;

import com.maesamco.content.global.security.authorization.AdminAuthorizationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code @RequireAdmin}을 실제로 강제하는 인터셉터를 등록한다(이슈 #311).
 *
 * <p>{@code InternalCallerAuthorizationConfig}(내부 API 호출자 인가)와 동일한 패턴 —
 * 대상이 되는 애노테이션이 없는 핸들러에는 아무 영향이 없으므로 전체 API 경로에 건다.</p>
 */
@Configuration
public class AdminAuthorizationConfig implements WebMvcConfigurer {

    @Bean
    public AdminAuthorizationInterceptor adminAuthorizationInterceptor() {
        return new AdminAuthorizationInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthorizationInterceptor())
                .addPathPatterns("/api/v1/**");
    }
}
