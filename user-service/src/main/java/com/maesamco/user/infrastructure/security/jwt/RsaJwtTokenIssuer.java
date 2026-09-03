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
     * {@inheritDoc}
     */
    @Override
    public IssuedTokens issueTokens(
            UUID userId,
            UserRole role,
            UUID sessionId
    ) {
        Objects.requireNonNull(userId, "사용자 식별자는 필수입니다.");
        Objects.requireNonNull(role, "사용자 역할은 필수입니다.");
        Objects.requireNonNull(sessionId, "세션 식별자는 필수입니다.");

        Instant issuedAt = clock.instant();
        Instant accessTokenExpiresAt =
                issuedAt.plus(jwtProperties.accessTokenTtl());
        Instant refreshTokenExpiresAt =
                issuedAt.plus(jwtProperties.refreshTokenTtl());

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
                .claim(ROLE_CLAIM, role.name())
                .claim(TOKEN_TYPE_CLAIM, TokenType.ACCESS.name())
                .claim(SESSION_ID_CLAIM, sessionId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(accessPrivateKey, Jwts.SIG.RS256)
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
                .claim(TOKEN_TYPE_CLAIM, TokenType.REFRESH.name())
                .claim(SESSION_ID_CLAIM, sessionId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(refreshPrivateKey, Jwts.SIG.RS256)
                .compact();
    }
}
