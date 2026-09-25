package com.maesamco.user.application.service;

import com.maesamco.user.application.port.SocialIdentityVerifier;
import com.maesamco.user.application.port.VerifiedSocialIdentity;
import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 소셜 재인증 시 ID Token의 sub가 로그인 사용자의 SocialAccount와 일치하는지 검증합니다(#328).
 */
@ExtendWith(MockitoExtension.class)
class SocialReauthenticatorTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ID_TOKEN = "google-id-token";
    private static final String GOOGLE_SUB = "google-sub-123";

    @Mock
    private SocialIdentityVerifier googleVerifier;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    private SocialReauthenticator socialReauthenticator;

    @BeforeEach
    void setUp() {
        when(googleVerifier.provider()).thenReturn(SocialProvider.GOOGLE);

        socialReauthenticator =
                new SocialReauthenticator(
                        List.of(googleVerifier),
                        socialAccountRepository
                );
    }

    @Test
    @DisplayName("ID Token의 sub가 로그인 사용자의 Google 계정이면 통과한다")
    void verifyOwnership_sameAccount() {
        // given
        when(googleVerifier.verify(ID_TOKEN)).thenReturn(identity(GOOGLE_SUB));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, GOOGLE_SUB))
                .thenReturn(Optional.of(SocialAccount.create(USER_ID, SocialProvider.GOOGLE, GOOGLE_SUB)));

        // when & then
        assertThatCode(
                () -> socialReauthenticator.verifyOwnership(USER_ID, SocialProvider.GOOGLE, ID_TOKEN)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 사용자에게 연결된 Google 계정이면 SOCIAL_REAUTH_ACCOUNT_MISMATCH")
    void verifyOwnership_otherUsersAccount() {
        // given
        when(googleVerifier.verify(ID_TOKEN)).thenReturn(identity(GOOGLE_SUB));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, GOOGLE_SUB))
                .thenReturn(Optional.of(SocialAccount.create(UUID.randomUUID(), SocialProvider.GOOGLE, GOOGLE_SUB)));

        // when & then
        assertError(ErrorCode.SOCIAL_REAUTH_ACCOUNT_MISMATCH);
    }

    @Test
    @DisplayName("어디에도 가입되지 않은 Google 계정이면 SOCIAL_REAUTH_ACCOUNT_MISMATCH (같은 오류로 존재 여부를 숨긴다)")
    void verifyOwnership_unlinkedAccount() {
        // given
        when(googleVerifier.verify(ID_TOKEN)).thenReturn(identity("other-sub"));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "other-sub"))
                .thenReturn(Optional.empty());

        // when & then
        assertError(ErrorCode.SOCIAL_REAUTH_ACCOUNT_MISMATCH);
    }

    @Test
    @DisplayName("유효하지 않은 ID Token은 401 대신 400 SOCIAL_REAUTH_FAILED로 응답한다 (클라이언트 토큰 재발급 루프 방지)")
    void verifyOwnership_invalidToken() {
        // given
        when(googleVerifier.verify(ID_TOKEN))
                .thenThrow(new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        // when & then
        assertError(ErrorCode.SOCIAL_REAUTH_FAILED);
        verify(socialAccountRepository, never()).findByProviderAndProviderUserId(org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("Google 인증 서비스 장애(SOCIAL_PROVIDER_UNAVAILABLE)는 그대로 전달한다")
    void verifyOwnership_providerUnavailable() {
        // given
        when(googleVerifier.verify(ID_TOKEN))
                .thenThrow(new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE));

        // when & then
        assertError(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("ID Token이 비어 있으면 Provider를 호출하지 않고 SOCIAL_REAUTH_FAILED")
    void verifyOwnership_blankToken() {
        assertThatThrownBy(
                () -> socialReauthenticator.verifyOwnership(USER_ID, SocialProvider.GOOGLE, " ")
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_REAUTH_FAILED);

        verify(googleVerifier, never()).verify(anyString());
    }

    @Test
    @DisplayName("Verifier가 없는 Provider는 SOCIAL_PROVIDER_NOT_SUPPORTED")
    void verifyOwnership_unsupportedProvider() {
        assertThatThrownBy(
                () -> socialReauthenticator.verifyOwnership(USER_ID, SocialProvider.KAKAO, ID_TOKEN)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_PROVIDER_NOT_SUPPORTED);
    }

    private VerifiedSocialIdentity identity(String sub) {
        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                sub,
                "learner@example.com",
                true
        );
    }

    private void assertError(ErrorCode errorCode) {
        assertThatThrownBy(
                () -> socialReauthenticator.verifyOwnership(USER_ID, SocialProvider.GOOGLE, ID_TOKEN)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
