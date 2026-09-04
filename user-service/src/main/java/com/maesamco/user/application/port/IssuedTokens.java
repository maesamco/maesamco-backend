package com.maesamco.user.application.port;

import java.time.Instant;

/**
 * 한 인증 세션에 대해 발급된 Access Token과 Refresh Token입니다.
 *
 * @param accessToken Access Token 문자열
 * @param accessTokenExpiresAt Access Token 만료 시각
 * @param refreshToken Refresh Token 문자열
 * @param refreshTokenExpiresAt Refresh Token 만료 시각
 */
public record IssuedTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}
