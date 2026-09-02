package com.maesamco.user.infrastructure.security.jwt;

import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.global.security.JwtProperties;
import com.maesamco.user.global.security.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RsaJwtTokenIssuer의 토큰 발급 규칙을 검증하는 단위 테스트입니다.
 */
class RsaJwtTokenIssuerTest {

    private static final Instant ISSUED_AT =
            Instant.parse("2026-09-02T00:00:00Z");

    private static final Duration ACCESS_TOKEN_TTL =
            Duration.ofMinutes(15);

    private static final Duration REFRESH_TOKEN_TTL =
            Duration.ofDays(14);

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final KeyPair KEY_PAIR = generateKeyPair();

    private final JwtProperties jwtProperties =
            new JwtProperties(
                    "unused-public-key",
                    "unused-private-key",
                    ACCESS_TOKEN_TTL,
                    REFRESH_TOKEN_TTL
            );

    private final TokenIssuer tokenIssuer =
            new RsaJwtTokenIssuer(
                    KEY_PAIR.getPrivate(),
                    jwtProperties,
                    Clock.fixed(
                            ISSUED_AT,
                            ZoneOffset.UTC
                    )
            );

    @Test
    @DisplayName("Access Token과 Refresh Token을 올바른 Claim으로 발급한다")
    void issueTokens_createsTokensWithExpectedClaims() {
        // when
        IssuedTokens tokens = tokenIssuer.issueTokens(
                USER_ID,
                UserRole.USER,
                SESSION_ID
        );

        // then
        Claims accessClaims =
                parseClaims(tokens.accessToken());

        Claims refreshClaims =
                parseClaims(tokens.refreshToken());

        assertThat(accessClaims.getSubject())
                .isEqualTo(USER_ID.toString());

        assertThat(accessClaims.get("role", String.class))
                .isEqualTo(UserRole.USER.name());

        assertThat(accessClaims.get("tokenType", String.class))
                .isEqualTo(TokenType.ACCESS.name());

        assertThat(accessClaims.get("sessionId", String.class))
                .isEqualTo(SESSION_ID.toString());

        assertThat(accessClaims.getIssuedAt().toInstant())
                .isEqualTo(ISSUED_AT);

        assertThat(accessClaims.getExpiration().toInstant())
                .isEqualTo(
                        ISSUED_AT.plus(ACCESS_TOKEN_TTL)
                );

        assertThat(accessClaims.getId())
                .isNotBlank();

        assertThat(refreshClaims.getSubject())
                .isEqualTo(USER_ID.toString());

        assertThat(refreshClaims.get("role"))
                .isNull();

        assertThat(refreshClaims.get("tokenType", String.class))
                .isEqualTo(TokenType.REFRESH.name());

        assertThat(refreshClaims.get("sessionId", String.class))
                .isEqualTo(SESSION_ID.toString());

        assertThat(refreshClaims.getIssuedAt().toInstant())
                .isEqualTo(ISSUED_AT);

        assertThat(refreshClaims.getExpiration().toInstant())
                .isEqualTo(
                        ISSUED_AT.plus(REFRESH_TOKEN_TTL)
                );

        assertThat(refreshClaims.getId())
                .isNotBlank();

        assertThat(accessClaims.getId())
                .isNotEqualTo(refreshClaims.getId());

        assertThat(tokens.accessToken())
                .isNotEqualTo(tokens.refreshToken());

        assertThat(tokens.accessTokenExpiresAt())
                .isEqualTo(
                        ISSUED_AT.plus(ACCESS_TOKEN_TTL)
                );

        assertThat(tokens.refreshTokenExpiresAt())
                .isEqualTo(
                        ISSUED_AT.plus(REFRESH_TOKEN_TTL)
                );
    }

    @Test
    @DisplayName("동일한 정보로 발급해도 매번 서로 다른 토큰을 생성한다")
    void issueTokens_generatesUniqueTokens() {
        // when
        IssuedTokens first = tokenIssuer.issueTokens(
                USER_ID,
                UserRole.USER,
                SESSION_ID
        );

        IssuedTokens second = tokenIssuer.issueTokens(
                USER_ID,
                UserRole.USER,
                SESSION_ID
        );

        // then
        assertThat(first.accessToken())
                .isNotEqualTo(second.accessToken());

        assertThat(first.refreshToken())
                .isNotEqualTo(second.refreshToken());
    }

    @Test
    @DisplayName("토큰 발급에 필요한 값이 없으면 예외가 발생한다")
    void issueTokens_rejectsNullArguments() {
        assertThatThrownBy(() -> tokenIssuer.issueTokens(
                null,
                UserRole.USER,
                SESSION_ID
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("사용자 식별자는 필수입니다.");

        assertThatThrownBy(() -> tokenIssuer.issueTokens(
                USER_ID,
                null,
                SESSION_ID
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("사용자 역할은 필수입니다.");

        assertThatThrownBy(() -> tokenIssuer.issueTokens(
                USER_ID,
                UserRole.USER,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("세션 식별자는 필수입니다.");
    }

    /**
     * 발급된 JWT를 테스트 공개키와 고정 시각으로 검증하고 Claim을 반환합니다.
     *
     * @param token 검증할 JWT
     * @return 검증된 JWT Claim
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(KEY_PAIR.getPublic())
                .clock(() -> Date.from(ISSUED_AT))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 테스트에 사용할 2048비트 RSA 키쌍을 생성합니다.
     *
     * @return RSA 키쌍
     */
    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("RSA");

            generator.initialize(2048);

            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "테스트 RSA 키쌍을 생성할 수 없습니다.",
                    exception
            );
        }
    }
}
