package com.maesamco.user.presentation.support;

import com.maesamco.user.application.port.IssuedTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenCookieFactoryTest {

    private static final Instant NOW =
            Instant.parse("2026-09-17T01:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            );

    @Test
    @DisplayName("설정된 보안 속성과 토큰 만료 시간을 반영해 Refresh Token Cookie를 생성한다")
    void create_appliesConfiguredAttributesAndExpiration() {
        IssuedTokens issuedTokens =
                mock(IssuedTokens.class);

        when(issuedTokens.refreshToken())
                .thenReturn("refresh-token");
        when(issuedTokens.refreshTokenExpiresAt())
                .thenReturn(
                        NOW.plusSeconds(300)
                );

        RefreshTokenCookieFactory factory =
                new RefreshTokenCookieFactory(
                        new AuthCookieProperties(
                                false,
                                "Strict",
                                "/custom-auth"
                        )
                );

        ResponseCookie cookie =
                factory.create(
                        issuedTokens,
                        CLOCK
                );

        assertThat(cookie.getName())
                .isEqualTo("refreshToken");
        assertThat(cookie.getValue())
                .isEqualTo("refresh-token");
        assertThat(cookie.isHttpOnly())
                .isTrue();
        assertThat(cookie.isSecure())
                .isFalse();
        assertThat(cookie.getSameSite())
                .isEqualTo("Strict");
        assertThat(cookie.getPath())
                .isEqualTo("/custom-auth");
        assertThat(cookie.getMaxAge())
                .isEqualTo(
                        Duration.ofSeconds(300)
                );
    }

    @Test
    @DisplayName("동일한 Cookie 정책으로 Refresh Token 삭제 Cookie를 생성한다")
    void createExpired_appliesConfiguredAttributes() {
        RefreshTokenCookieFactory factory =
                new RefreshTokenCookieFactory(
                        new AuthCookieProperties(
                                true,
                                "Lax",
                                "/api/v1/auth"
                        )
                );

        ResponseCookie cookie =
                factory.createExpired();

        assertThat(cookie.getName())
                .isEqualTo("refreshToken");
        assertThat(cookie.getValue())
                .isEmpty();
        assertThat(cookie.isHttpOnly())
                .isTrue();
        assertThat(cookie.isSecure())
                .isTrue();
        assertThat(cookie.getSameSite())
                .isEqualTo("Lax");
        assertThat(cookie.getPath())
                .isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge())
                .isEqualTo(Duration.ZERO);
    }
}