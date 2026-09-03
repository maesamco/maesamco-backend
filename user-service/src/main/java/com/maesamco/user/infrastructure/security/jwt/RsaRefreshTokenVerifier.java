package com.maesamco.user.infrastructure.security.jwt;

import com.maesamco.user.application.port.RefreshTokenVerifier;
import com.maesamco.user.application.port.VerifiedRefreshToken;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.security.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

/**
 * RSA 공개키를 사용하여 Refresh Token을 검증하는 구현체입니다.
 *
 * <p>Access Token과 Refresh Token의 서명 키를 분리하고 있으므로
 * Refresh Token 전용 공개키인 {@code jwtRefreshPublicKey}만 사용합니다.</p>
 */
@Component
public class RsaRefreshTokenVerifier implements RefreshTokenVerifier {

    private final PublicKey refreshPublicKey;
    private final Clock clock;

    public RsaRefreshTokenVerifier(
            @Qualifier("jwtRefreshPublicKey")
            PublicKey refreshPublicKey,
            Clock clock
    ) {
        this.refreshPublicKey = refreshPublicKey;
        this.clock = clock;
    }

    /**
     * Refresh Token의 서명, 만료시간, 토큰 타입 및 필수 Claim을 검증합니다.
     *
     * @param refreshToken 검증할 원본 Refresh Token
     * @return 검증을 통과한 Refresh Token 정보
     * @throws BusinessException 토큰이 만료되었거나 유효하지 않은 경우
     */
    @Override
    public VerifiedRefreshToken verify(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(refreshPublicKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();

            validateTokenType(claims);

            String subject = claims.getSubject();
            String sessionIdClaim =
                    claims.get("sessionId", String.class);
            String tokenId = claims.getId();
            Date expiration = claims.getExpiration();

            validateRequiredClaims(
                    subject,
                    sessionIdClaim,
                    tokenId,
                    expiration
            );

            UUID userId = UUID.fromString(subject);
            UUID sessionId = UUID.fromString(sessionIdClaim);

            return new VerifiedRefreshToken(
                    userId,
                    sessionId,
                    tokenId,
                    expiration.toInstant()
            );
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(
                    ErrorCode.AUTH_EXPIRED_TOKEN
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }
    }

    /**
     * JWT가 Refresh Token인지 확인합니다.
     *
     * @param claims 검증할 JWT Claim
     */
    private void validateTokenType(Claims claims) {
        String tokenType =
                claims.get("tokenType", String.class);

        if (!TokenType.REFRESH.name().equals(tokenType)) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }
    }

    /**
     * Refresh 처리에 필요한 필수 Claim이 존재하는지 확인합니다.
     *
     * @param subject 사용자 식별자 Claim
     * @param sessionId 세션 식별자 Claim
     * @param tokenId JWT ID
     * @param expiration 토큰 만료 시각
     */
    private void validateRequiredClaims(
            String subject,
            String sessionId,
            String tokenId,
            Date expiration
    ) {
        if (subject == null || subject.isBlank()) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }

        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }

        if (tokenId == null || tokenId.isBlank()) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }

        if (expiration == null) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }
    }
}
