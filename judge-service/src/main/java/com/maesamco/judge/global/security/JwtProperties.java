package com.maesamco.judge.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 공개키(검증용) 설정.
 * Private Key는 User Service만 보관한다 — 다른 서비스/Gateway는 이 Public Key만 가진다.
 *
 * application.yml 예:
 *   jwt:
 *     public-key: ${JWT_PUBLIC_KEY}   # PEM 형식 문자열(개행은 \n 이스케이프) 또는 파일 경로
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String publicKey) {
}
