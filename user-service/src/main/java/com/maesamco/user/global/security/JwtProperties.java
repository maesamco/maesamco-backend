package com.maesamco.user.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 서명 키와 토큰 만료시간 설정입니다.
 *
 * <p>Access Token과 Refresh Token은 서로 다른 RSA 키쌍을 사용합니다.</p>
 *
 * <p>Access Token 공개키는 Gateway와 각 서비스가 검증에 사용할 수 있도록
 * 공유하며, Access Token 개인키는 발급 주체인 User Service에서만 보관합니다.
 * Refresh Token 키쌍은 Refresh Token 발급 및 검증을 담당하는
 * User Service에서만 사용합니다.</p>
 *
 * @param publicKey Access Token 검증용 RSA 공개키 PEM 문자열
 * @param privateKey Access Token 서명용 RSA 개인키 PKCS#8 PEM 문자열
 * @param refreshPublicKey Refresh Token 검증용 RSA 공개키 PEM 문자열
 * @param refreshPrivateKey Refresh Token 서명용 RSA 개인키 PKCS#8 PEM 문자열
 * @param accessTokenTtl Access Token 유효시간
 * @param refreshTokenTtl Refresh Token 유효시간
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String publicKey,
        String privateKey,
        String refreshPublicKey,
        String refreshPrivateKey,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
