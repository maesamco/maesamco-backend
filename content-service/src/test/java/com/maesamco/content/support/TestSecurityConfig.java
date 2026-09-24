package com.maesamco.content.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 컨트롤러 슬라이스 테스트({@code @WebMvcTest})가 공통으로 쓰는 보안 설정이다.
 *
 * <p>URL 단위 인가는 모두 열어두고(JWT 검증 필터 없음) 메서드 보안({@code @PreAuthorize} 등)만 실제로 동작시킨다.
 * 테스트가 {@code SecurityContext}에 직접 인증 정보를 넣어 권한별 동작을 검증하는 용도다.</p>
 *
 * <p>{@code proxyTargetClass = true}는 운영 {@code SecurityConfig}와 같아야 한다. 컨트롤러가 {@code *ApiDocs}
 * 인터페이스를 구현하면 기본값(JDK 동적 프록시)에서는 구현체에만 붙은 매핑·커스텀 애노테이션이
 * {@code HandlerMethod}에 보이지 않아 라우트가 404가 되거나 검사가 조용히 무시된다(#313, #316).
 * 이전에는 컨트롤러 테스트마다 같은 설정을 각자 복사해 두었고, 그 사이 일부가 서로 어긋나 있었다.</p>
 *
 * <p>실제 {@code SecurityConfig}(JWT 필터 체인)를 검증해야 하는 테스트는 이 클래스 대신
 * {@code @Import(SecurityConfig.class)}를 쓴다({@code UnitControllerSecurityTest} 참고).</p>
 */
@TestConfiguration
@EnableMethodSecurity(proxyTargetClass = true)
public class TestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
