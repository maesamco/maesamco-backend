package com.maesamco.user.presentation.support;

import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.global.security.TokenExpirationCalculator;
import org.springframework.http.ResponseCookie;

import java.time.Clock;
import java.time.Duration;

/**
 * Refresh Token Cookie 생성 정책을 관리합니다.
 */
public final class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME =
            "refreshToken";

    private final AuthCookieProperties properties;

    public RefreshTokenCookieFactory(
            AuthCookieProperties properties
    ) {
        this.properties = properties;
    }

    /**
     * 발급된 Refresh Token을 HttpOnly Cookie로 생성합니다.
     *
     * @param issuedTokens 발급된 인증 토큰
     * @param clock 현재 시각 계산용 Clock
     * @return Refresh Token Cookie
     */
    public ResponseCookie create(
            IssuedTokens issuedTokens,
            Clock clock
    ) {
        long maxAgeSeconds =
                TokenExpirationCalculator.remainingSeconds(
                        clock.instant(),
                        issuedTokens.refreshTokenExpiresAt()
                );

        return baseBuilder(
                issuedTokens.refreshToken()
        )
                .maxAge(
                        Duration.ofSeconds(maxAgeSeconds)
                )
                .build();
    }

    /**
     * 브라우저의 Refresh Token Cookie를 삭제하기 위한
     * 만료 Cookie를 생성합니다.
     *
     * @return 만료된 Refresh Token Cookie
     */
    public ResponseCookie createExpired() {
        return baseBuilder("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(
            String value
    ) {
        return ResponseCookie
                .from(
                        COOKIE_NAME,
                        value
                )
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path());
    }
}
