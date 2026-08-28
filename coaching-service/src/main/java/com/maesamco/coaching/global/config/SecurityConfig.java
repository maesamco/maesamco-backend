package com.maesamco.coaching.global.config;

import com.maesamco.coaching.global.security.JwtAuthenticationFilter;
import com.maesamco.coaching.global.security.JwtProperties;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.util.Base64;

/**
 * Gateway 뒤에서 동작하는 서비스 공통 보안 설정.
 * HTTP 요청 자체는 필터 체인에서 막지 않고(permitAll), 실제 권한은
 * 각 서비스 메서드의 @PreAuthorize / IDOR 소유권 검증 로직에서 판단한다
 * (게이트웨이 및 인증 보안 설계 1절 — 인증/인가 책임 분리 원칙).
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PublicKey jwtPublicKey(JwtProperties jwtProperties) throws Exception {
        String pem = jwtProperties.publicKey()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        // 서명 알고리즘은 User Service의 키 발급 방식과 일치해야 한다(예: RSA/RS256, EdDSA 등).
        // 알고리즘이 달라지면 KeyFactory.getInstance(...) 인자도 함께 바꿔야 한다.
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(PublicKey jwtPublicKey) {
        return new JwtAuthenticationFilter(jwtPublicKey);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }
}
