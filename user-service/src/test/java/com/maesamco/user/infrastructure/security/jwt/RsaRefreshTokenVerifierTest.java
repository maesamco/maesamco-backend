package com.maesamco.user.infrastructure.security.jwt;

import com.maesamco.user.application.port.VerifiedRefreshToken;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.security.TokenType;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RsaRefreshTokenVerifier의 Refresh Token 검증 규칙을 검증하는 단위 테스트입니다.
 */
class RsaRefreshTokenVerifierTest {

    private static final Instant NOW =
            Instant.parse("2026-09-03T10:00:00Z");

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final String TOKEN_ID =
            "33333333-3333-3333-3333-333333333333";

    private static final KeyPair REFRESH_KEY_PAIR =
            generateKeyPair();

    private static final KeyPair OTHER_KEY_PAIR =
            generateKeyPair();

    private final RsaRefreshTokenVerifier verifier =
            new RsaRefreshTokenVerifier(
                    REFRESH_KEY_PAIR.getPublic(),
                    Clock.fixed(
                            NOW,
                            ZoneOffset.UTC
                    )
            );

    @Test
    @DisplayName("유효한 Refresh Token의 필수 Claim을 검증하고 반환한다")
    void verify_validRefreshToken_returnsVerifiedToken() {
        // given
        Instant expiresAt = NOW.plusSeconds(3600);

        String refreshToken = createToken(
                USER_ID.toString(),
                SESSION_ID.toString(),
                TOKEN_ID,
                TokenType.REFRESH,
                NOW.minusSeconds(60),
                expiresAt,
                REFRESH_KEY_PAIR
        );

        // when
        VerifiedRefreshToken verifiedToken =
                verifier.verify(refreshToken);

        // then
        assertThat(verifiedToken.userId())
                .isEqualTo(USER_ID);

        assertThat(verifiedToken.sessionId())
                .isEqualTo(SESSION_ID);

        assertThat(verifiedToken.tokenId())
                .isEqualTo(TOKEN_ID);

        assertThat(verifiedToken.expiresAt())
                .isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("Refresh 공개키로 검증되더라도 tokenType이 ACCESS이면 거부한다")
    void verify_accessTokenType_throwsInvalidToken() {
        // given
        String token = createToken(
                USER_ID.toString(),
                SESSION_ID.toString(),
                TOKEN_ID,
                TokenType.ACCESS,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                REFRESH_KEY_PAIR
        );

        // when & then
        assertBusinessException(
                () -> verifier.verify(token),
                ErrorCode.AUTH_INVALID_TOKEN
        );
    }

    @Test
    @DisplayName("만료된 Refresh Token이면 AUTH_EXPIRED_TOKEN을 반환한다")
    void verify_expiredRefreshToken_throwsExpiredToken() {
        // given
        String token = createToken(
                USER_ID.toString(),
                SESSION_ID.toString(),
                TOKEN_ID,
                TokenType.REFRESH,
                NOW.minusSeconds(7200),
                NOW.minusSeconds(3600),
                REFRESH_KEY_PAIR
        );

        // when & then
        assertBusinessException(
                () -> verifier.verify(token),
                ErrorCode.AUTH_EXPIRED_TOKEN
        );
    }

    @Test
    @DisplayName("다른 RSA 개인키로 서명된 Refresh Token이면 거부한다")
    void verify_tokenSignedWithDifferentKey_throwsInvalidToken() {
        // given
        String token = createToken(
                USER_ID.toString(),
                SESSION_ID.toString(),
                TOKEN_ID,
                TokenType.REFRESH,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                OTHER_KEY_PAIR
        );

        // when & then
        assertBusinessException(
                () -> verifier.verify(token),
                ErrorCode.AUTH_INVALID_TOKEN
        );
    }

    @Test
    @DisplayName("Refresh Token이 비어 있으면 거부한다")
    void verify_blankToken_throwsInvalidToken() {
        assertBusinessException(
                () -> verifier.verify(" "),
                ErrorCode.AUTH_INVALID_TOKEN
        );
    }

    @Test
    @DisplayName("사용자 식별자가 UUID 형식이 아니면 거부한다")
    void verify_invalidUserIdClaim_throwsInvalidToken() {
        // given
        String token = createToken(
                "invalid-user-id",
                SESSION_ID.toString(),
                TOKEN_ID,
                TokenType.REFRESH,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                REFRESH_KEY_PAIR
        );

        // when & then
        assertBusinessException(
                () -> verifier.verify(token),
                ErrorCode.AUTH_INVALID_TOKEN
        );
    }

    /**
     * 테스트 조건에 맞는 JWT를 생성합니다.
     *
     * @param subject 사용자 식별자
     * @param sessionId 세션 식별자
     * @param tokenId JWT ID
     * @param tokenType 토큰 타입
     * @param issuedAt 발급 시각
     * @param expiresAt 만료 시각
     * @param keyPair 서명에 사용할 RSA 키쌍
     * @return 생성된 JWT
     */
    private String createToken(
            String subject,
            String sessionId,
            String tokenId,
            TokenType tokenType,
            Instant issuedAt,
            Instant expiresAt,
            KeyPair keyPair
    ) {
        return Jwts.builder()
                .id(tokenId)
                .subject(subject)
                .claim(
                        "tokenType",
                        tokenType.name()
                )
                .claim(
                        "sessionId",
                        sessionId
                )
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(
                        keyPair.getPrivate(),
                        Jwts.SIG.RS256
                )
                .compact();
    }

    /**
     * BusinessException의 ErrorCode를 검증합니다.
     *
     * @param action 예외가 발생할 동작
     * @param expectedErrorCode 기대하는 에러 코드
     */
    private void assertBusinessException(
            Runnable action,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception)
                                .getErrorCode()
                )
                .isEqualTo(expectedErrorCode);
    }

    /**
     * 테스트용 2048비트 RSA 키쌍을 생성합니다.
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
