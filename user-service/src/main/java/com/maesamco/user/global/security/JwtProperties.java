package com.maesamco.user.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 서명 키와 토큰 만료시간 설정입니다.
 *
 * <p>Public Key는 JWT 검증에 사용하며 모든 서비스와 Gateway가 공유합니다.
 * Private Key는 JWT 발급 주체인 User Service에서만 보관합니다.</p>
 *
 * @param publicKey RSA 공개키 PEM 문자열
 * @param privateKey RSA 개인키 PKCS#8 PEM 문자열
 * @param accessTokenTtl Access Token 유효시간
 * @param refreshTokenTtl Refresh Token 유효시간
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String publicKey,
        String privateKey,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
