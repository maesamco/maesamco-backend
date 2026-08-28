package com.maesamco.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS는 Gateway 한 곳에서만 설정한다(게이트웨이 및 인증 보안 설계 9절).
 * 설정만 해두고 끝내지 말 것 — 배포 전 체크리스트에 "실제 브라우저 fetch로 프리플라이트까지
 * 검증"이 포함되어 있다(팀 컨벤션에는 명시 안 됐지만 설계 문서 9절 원칙을 그대로 따름).
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        // TODO: 착수 시 프론트엔드 배포 도메인이 정해지면 "*" 대신 명시적 Origin으로 교체
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }
}
