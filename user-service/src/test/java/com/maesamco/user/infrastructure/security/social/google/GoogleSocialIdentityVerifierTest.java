package com.maesamco.user.infrastructure.security.social.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.maesamco.user.application.port.VerifiedSocialIdentity;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Google ID Token Adapter의 파싱 및 오류 매핑을 검증합니다.
 *
 * <p>실제 Google 네트워크 호출은 수행하지 않고
 * GoogleIdTokenVerifier를 Mock하여 Adapter 책임만 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class GoogleSocialIdentityVerifierTest {

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    private GoogleSocialIdentityVerifier
            socialIdentityVerifier;

    @BeforeEach
    void setUp() {
        socialIdentityVerifier =
                new GoogleSocialIdentityVerifier(
                        googleIdTokenVerifier
                );
    }

    @Test
    @DisplayName(
            "Google Provider를 반환한다"
    )
    void provider() {
        assertThat(
                socialIdentityVerifier.provider()
        ).isEqualTo(
                SocialProvider.GOOGLE
        );
    }

    @Test
    @DisplayName(
            "유효한 Google ID Token에서 sub와 이메일을 추출한다"
    )
    void verify_validToken() throws Exception {
        // given
        String token =
                createToken(
                        "google-sub-123",
                        "Learner@Example.com",
                        true
                );

        when(
                googleIdTokenVerifier.verify(
                        any(GoogleIdToken.class)
                )
        ).thenReturn(true);

        // when
        VerifiedSocialIdentity identity =
                socialIdentityVerifier.verify(
                        token
                );

        // then
        assertThat(identity.provider())
                .isEqualTo(
                        SocialProvider.GOOGLE
                );

        assertThat(identity.providerUserId())
                .isEqualTo(
                        "google-sub-123"
                );

        assertThat(identity.email())
                .isEqualTo(
                        "Learner@Example.com"
                );

        assertThat(identity.emailVerified())
                .isTrue();
    }

    @Test
    @DisplayName(
            "Google 이메일 인증 여부를 Identity에 그대로 전달한다"
    )
    void verify_unverifiedEmail() throws Exception {
        // given
        String token =
                createToken(
                        "google-sub-456",
                        "learner@example.com",
                        false
                );

        when(
                googleIdTokenVerifier.verify(
                        any(GoogleIdToken.class)
                )
        ).thenReturn(true);

        // when
        VerifiedSocialIdentity identity =
                socialIdentityVerifier.verify(
                        token
                );

        // then
        assertThat(
                identity.emailVerified()
        ).isFalse();
    }

    @Test
    @DisplayName(
            "Google 검증에 실패한 Token은 거부한다"
    )
    void verify_rejectsInvalidToken() throws Exception {
        // given
        String token =
                createToken(
                        "google-sub-123",
                        "learner@example.com",
                        true
                );

        when(
                googleIdTokenVerifier.verify(
                        any(GoogleIdToken.class)
                )
        ).thenReturn(false);

        // when & then
        assertThatThrownBy(
                () -> socialIdentityVerifier.verify(
                        token
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.AUTH_INVALID_TOKEN
                );
    }

    @Test
    @DisplayName(
            "형식이 잘못된 Google Token은 검증 전에 거부한다"
    )
    void verify_rejectsMalformedToken() {
        assertThatThrownBy(
                () -> socialIdentityVerifier.verify(
                        "not-a-jwt"
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.AUTH_INVALID_TOKEN
                );

        try {
            verify(
                    googleIdTokenVerifier,
                    never()
            ).verify(
                    any(GoogleIdToken.class)
            );
        } catch (
                GeneralSecurityException
                | IOException exception
        ) {
            throw new AssertionError(
                    exception
            );
        }
    }

    @Test
    @DisplayName(
            "Google 공개키 조회 I/O 실패는 Provider 장애로 처리한다"
    )
    void verify_providerUnavailable() throws Exception {
        // given
        String token =
                createToken(
                        "google-sub-123",
                        "learner@example.com",
                        true
                );

        when(
                googleIdTokenVerifier.verify(
                        any(GoogleIdToken.class)
                )
        ).thenThrow(
                new IOException(
                        "Google public key unavailable"
                )
        );

        // when & then
        assertThatThrownBy(
                () -> socialIdentityVerifier.verify(
                        token
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode
                                .SOCIAL_PROVIDER_UNAVAILABLE
                );
    }

    @Test
    @DisplayName(
            "Google Token 보안 검증 예외는 유효하지 않은 Token으로 처리한다"
    )
    void verify_securityFailure() throws Exception {
        // given
        String token =
                createToken(
                        "google-sub-123",
                        "learner@example.com",
                        true
                );

        when(
                googleIdTokenVerifier.verify(
                        any(GoogleIdToken.class)
                )
        ).thenThrow(
                new GeneralSecurityException(
                        "invalid signature"
                )
        );

        // when & then
        assertThatThrownBy(
                () -> socialIdentityVerifier.verify(
                        token
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.AUTH_INVALID_TOKEN
                );
    }

    @Test
    @DisplayName(
            "sub가 없는 Google Token은 거부한다"
    )
    void verify_rejectsMissingSubject() throws Exception {
        // given
        String token =
                createToken(
                        "",
                        "learner@example.com",
                        true
                );

        when(
                googleIdTokenVerifier.verify(
                        any(GoogleIdToken.class)
                )
        ).thenReturn(true);

        // when & then
        assertThatThrownBy(
                () -> socialIdentityVerifier.verify(
                        token
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.AUTH_INVALID_TOKEN
                );
    }

    /**
     * GoogleIdToken.parse()가 읽을 수 있는
     * 테스트용 JWT 문자열을 생성합니다.
     *
     * <p>서명 자체는 Mock Verifier가 처리하므로
     * 이 메서드는 JWT 구조와 Claim만 생성합니다.</p>
     */
    private String createToken(
            String subject,
            String email,
            boolean emailVerified
    ) {
        String header =
                """
                {
                  "alg": "RS256",
                  "kid": "test-key"
                }
                """;

        String payload =
                """
                {
                  "iss": "https://accounts.google.com",
                  "aud": "test-google-client-id.apps.googleusercontent.com",
                  "sub": "%s",
                  "email": "%s",
                  "email_verified": %s,
                  "iat": 1789900000,
                  "exp": 1899900000
                }
                """.formatted(
                        subject,
                        email,
                        emailVerified
                );

        return encode(header)
                + "."
                + encode(payload)
                + "."
                + encode("signature");
    }

    private String encode(
            String value
    ) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        value.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
    }
}
