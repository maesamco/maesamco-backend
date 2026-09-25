package com.maesamco.user.application.service;

import com.maesamco.user.application.port.SocialIdentityVerifier;
import com.maesamco.user.application.port.VerifiedSocialIdentity;
import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 로그인된 사용자가 소셜 Provider로 본인임을 다시 증명하는지 확인합니다(#328).
 *
 * <p>비밀번호가 없는 소셜 회원의 회원 탈퇴처럼 민감한 작업 전에 사용합니다.
 * Provider가 검증한 사용자 식별자(Google은 OIDC sub)가 이 사용자에게
 * 연결된 SocialAccount와 일치해야 합니다. 이메일은 비교하지 않습니다.</p>
 *
 * <p>외부 Provider 검증이 포함되므로 DB 행 잠금을 잡기 전에 호출해야 합니다.</p>
 */
@Service
public class SocialReauthenticator {

    private final Map<SocialProvider, SocialIdentityVerifier> identityVerifiers;
    private final SocialAccountRepository socialAccountRepository;

    public SocialReauthenticator(
            List<SocialIdentityVerifier> identityVerifiers,
            SocialAccountRepository socialAccountRepository
    ) {
        this.identityVerifiers =
                SocialIdentityVerifiers.toMap(identityVerifiers);
        this.socialAccountRepository =
                socialAccountRepository;
    }

    /**
     * Credential이 해당 사용자에게 연결된 소셜 계정의 것인지 확인합니다.
     *
     * @param userId 로그인된 사용자 식별자
     * @param provider 재인증에 사용할 소셜 인증 제공자
     * @param credential Provider 인증 Credential (Google ID Token)
     * @throws BusinessException Credential이 유효하지 않으면 SOCIAL_REAUTH_FAILED,
     *                           다른 소셜 계정이면 SOCIAL_REAUTH_ACCOUNT_MISMATCH
     */
    public void verifyOwnership(
            UUID userId,
            SocialProvider provider,
            String credential
    ) {
        Objects.requireNonNull(userId, "사용자 식별자는 필수입니다.");
        Objects.requireNonNull(provider, "소셜 로그인 Provider는 필수입니다.");

        SocialIdentityVerifier verifier =
                identityVerifiers.get(provider);

        if (verifier == null) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_PROVIDER_NOT_SUPPORTED
            );
        }

        VerifiedSocialIdentity identity =
                verify(verifier, credential);

        if (identity == null || identity.provider() != provider) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_REAUTH_FAILED
            );
        }

        /*
         * 다른 사람의 Google 계정이든, 가입에 쓰지 않은 본인의 다른 Google 계정이든
         * 같은 오류로 응답합니다. 어떤 계정이 가입돼 있는지 드러내지 않습니다.
         */
        SocialAccount socialAccount =
                socialAccountRepository
                        .findByProviderAndProviderUserId(
                                provider,
                                identity.providerUserId()
                        )
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.SOCIAL_REAUTH_ACCOUNT_MISMATCH
                                )
                        );

        if (!socialAccount.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_REAUTH_ACCOUNT_MISMATCH
            );
        }
    }

    /**
     * 로그인된 상태의 재인증 실패는 401 대신 400(SOCIAL_REAUTH_FAILED)으로 바꿉니다.
     * 401은 클라이언트가 Access Token 만료로 오해해 재발급·재시도 루프에 빠질 수 있습니다.
     * Provider 장애(SOCIAL_PROVIDER_UNAVAILABLE, 503)는 그대로 전달합니다.
     */
    private VerifiedSocialIdentity verify(
            SocialIdentityVerifier verifier,
            String credential
    ) {
        if (credential == null || credential.isBlank()) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_REAUTH_FAILED
            );
        }

        try {
            return verifier.verify(credential);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.AUTH_INVALID_TOKEN) {
                throw new BusinessException(
                        ErrorCode.SOCIAL_REAUTH_FAILED
                );
            }

            throw exception;
        }
    }
}
