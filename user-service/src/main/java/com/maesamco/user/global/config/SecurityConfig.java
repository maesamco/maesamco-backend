package com.maesamco.user.global.config;

import com.maesamco.user.global.security.JwtAuthenticationFilter;
import com.maesamco.user.global.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
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

    /**
     * JWT 검증에 사용할 RSA 공개키를 생성합니다.
     *
     * @param jwtProperties JWT 설정
     * @return RSA 공개키
     * @throws GeneralSecurityException 공개키를 생성할 수 없는 경우
     */
    @Bean
    public PublicKey jwtPublicKey(
            JwtProperties jwtProperties
    ) throws GeneralSecurityException {
        String pem = jwtProperties.publicKey()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(pem);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(
                new X509EncodedKeySpec(decoded)
        );
    }

    /**
     * JWT 발급 서명에 사용할 RSA 개인키를 생성합니다.
     *
     * @param jwtProperties JWT 설정
     * @return RSA 개인키
     * @throws GeneralSecurityException 개인키를 생성할 수 없는 경우
     */
    @Bean
    public PrivateKey jwtPrivateKey(
            JwtProperties jwtProperties
    ) throws GeneralSecurityException {
        String pem = jwtProperties.privateKey()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(pem);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(decoded)
        );
    }

    /**
     * JWT 발급 시각 계산에 사용할 UTC Clock입니다.
     *
     * @return 시스템 UTC Clock
     */
    @Bean
    public Clock jwtClock() {
        return Clock.systemUTC();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            PublicKey jwtPublicKey
    ) {
        return new JwtAuthenticationFilter(jwtPublicKey);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**"
                        )
                        .permitAll()
                        .anyRequest()
                        .permitAll()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        AnonymousAuthenticationFilter.class
                );

        return http.build();
    }
}
