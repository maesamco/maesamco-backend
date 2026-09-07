package com.maesamco.user.infrastructure.security.jwt;

import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.global.security.JwtProperties;
import com.maesamco.user.global.security.TokenType;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * 서로 다른 RSA 개인키로 Access Token과 Refresh Token을 발급합니다.
 *
 * <p>두 토큰에는 동일한 사용자 및 세션 식별자가 포함되며,
 * 각 토큰은 별도의 JWT ID를 가집니다.</p>
 *
 * <p>신규 인증 세션에서는 설정된 Refresh Token TTL을 기준으로
 * 만료 시각을 계산하고, Refresh Token Rotation에서는 기존 인증
 * 세션의 절대 만료 시각을 그대로 유지합니다.</p>
 */
@Component
public class RsaJwtTokenIssuer implements TokenIssuer {

    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String SESSION_ID_CLAIM = "sessionId";

    private final PrivateKey accessPrivateKey;
    private final PrivateKey refreshPrivateKey;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    /**
     * JWT 발급 구현체를 생성합니다.
     *
     * @param accessPrivateKey Access Token RS256 서명에 사용할 RSA 개인키
     * @param refreshPrivateKey Refresh Token RS256 서명에 사용할 RSA 개인키
     * @param jwtProperties 토큰 만료시간 설정
     * @param clock 토큰 발급 시각 계산에 사용할 Clock
     */
    public RsaJwtTokenIssuer(
            @Qualifier("jwtPrivateKey")
            PrivateKey accessPrivateKey,
            @Qualifier("jwtRefreshPrivateKey")
            PrivateKey refreshPrivateKey,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.accessPrivateKey = accessPrivateKey;
        this.refreshPrivateKey = refreshPrivateKey;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /**
     * 신규 인증 세션에서 사용할 Access Token과 Refresh Token을 발급합니다.
     *
     * <p>회원가입과 로그인 시 사용하며, Refresh Token 만료 시각은
     * 현재 발급 시각에 설정된 Refresh Token TTL을 더해 계산합니다.</p>
     */
    @Override
    public IssuedTokens issueTokens(
            UUID userId,
            UserRole role,
            UUID sessionId
    ) {
        validateRequiredArguments(
                userId,
                role,
                sessionId
        );

        Instant issuedAt = clock.instant();
        Instant refreshTokenExpiresAt =
                issuedAt.plus(jwtProperties.refreshTokenTtl());

        return issueTokens(
                userId,
                role,
                sessionId,
                issuedAt,
                refreshTokenExpiresAt
        );
    }

    /**
     * 기존 인증 세션에서 Refresh Token을 Rotation하며
     * 새로운 Access Token과 Refresh Token을 발급합니다.
     *
     * <p>Refresh Token의 만료 시각은 새로 계산하지 않고,
     * 기존 AuthSession의 절대 만료 시각을 그대로 사용합니다.
     * 따라서 Refresh를 반복해도 세션 수명이 연장되지 않습니다.</p>
     */
    @Override
    public IssuedTokens issueRotatedTokens(
            UUID userId,
            UserRole role,
            UUID sessionId,
            Instant refreshTokenExpiresAt
    ) {
        validateRequiredArguments(
                userId,
                role,
                sessionId
        );

        Objects.requireNonNull(
                refreshTokenExpiresAt,
                "Refresh Token 만료 시각은 필수입니다."
        );

        Instant issuedAt = clock.instant();

        if (!refreshTokenExpiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                    "Refresh Token 만료 시각은 현재 시각보다 이후여야 합니다."
            );
        }

        return issueTokens(
                userId,
                role,
                sessionId,
                issuedAt,
                refreshTokenExpiresAt
        );
    }

    /**
     * 지정된 발급 시각과 Refresh Token 만료 시각을 기준으로
     * Access Token과 Refresh Token을 생성합니다.
     */
    private IssuedTokens issueTokens(
            UUID userId,
            UserRole role,
            UUID sessionId,
            Instant issuedAt,
            Instant refreshTokenExpiresAt
    ) {
        Instant accessTokenExpiresAt =
                issuedAt.plus(jwtProperties.accessTokenTtl());

        String accessToken = createAccessToken(
                userId,
                role,
                sessionId,
                issuedAt,
                accessTokenExpiresAt
        );

        String refreshToken = createRefreshToken(
                userId,
                sessionId,
                issuedAt,
                refreshTokenExpiresAt
        );

        return new IssuedTokens(
                accessToken,
                accessTokenExpiresAt,
                refreshToken,
                refreshTokenExpiresAt
        );
    }

    /**
     * 토큰 발급에 공통으로 필요한 값을 검증합니다.
     */
    private void validateRequiredArguments(
            UUID userId,
            UserRole role,
            UUID sessionId
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                role,
                "사용자 역할은 필수입니다."
        );

        Objects.requireNonNull(
                sessionId,
                "세션 식별자는 필수입니다."
        );
    }

    /**
     * 사용자 역할을 포함한 Access Token을 생성합니다.
     */
    private String createAccessToken(
            UUID userId,
            UserRole role,
            UUID sessionId,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(
                        ROLE_CLAIM,
                        role.name()
                )
                .claim(
                        TOKEN_TYPE_CLAIM,
                        TokenType.ACCESS.name()
                )
                .claim(
                        SESSION_ID_CLAIM,
                        sessionId.toString()
                )
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(
                        accessPrivateKey,
                        Jwts.SIG.RS256
                )
                .compact();
    }

    /**
     * 세션 갱신에 사용할 Refresh Token을 생성합니다.
     */
    private String createRefreshToken(
            UUID userId,
            UUID sessionId,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(
                        TOKEN_TYPE_CLAIM,
                        TokenType.REFRESH.name()
                )
                .claim(
                        SESSION_ID_CLAIM,
                        sessionId.toString()
                )
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(
                        refreshPrivateKey,
                        Jwts.SIG.RS256
                )
                .compact();
    }
}
