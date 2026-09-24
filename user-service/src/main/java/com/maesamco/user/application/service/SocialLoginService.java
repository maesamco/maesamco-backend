package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.SocialIdentityVerifier;
import com.maesamco.user.application.port.SocialSignupTokenIssuer;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.application.port.VerifiedSocialIdentity;
import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.security.TokenExpirationCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 소셜 인증 결과를 MAESAMCO 계정과 연결하여
 * 기존 사용자 로그인 또는 신규 회원가입 진입을 결정합니다.
 *
 * <p>중요한 정책은 다음과 같습니다.</p>
 *
 * <p>1. SocialAccount는 이메일이 아니라
 * provider + providerUserId로 먼저 조회합니다.</p>
 *
 * <p>2. 기존 SocialAccount가 없을 때만 이메일 중복을 확인합니다.</p>
 *
 * <p>3. 동일 이메일의 일반 회원이 존재하면
 * 자동 연결하지 않고 신규 소셜 회원가입을 차단합니다.</p>
 */
@Service
public class SocialLoginService {

    private final Map<SocialProvider, SocialIdentityVerifier>
            identityVerifiers;

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final EmailNormalizer emailNormalizer;
    private final EmailLookupHasher emailLookupHasher;
    private final SocialSignupTokenIssuer socialSignupTokenIssuer;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthSessionStore authSessionStore;
    private final Clock clock;

    public SocialLoginService(
            List<SocialIdentityVerifier> identityVerifiers,
            SocialAccountRepository socialAccountRepository,
            UserRepository userRepository,
            EmailNormalizer emailNormalizer,
            EmailLookupHasher emailLookupHasher,
            SocialSignupTokenIssuer socialSignupTokenIssuer,
            TokenIssuer tokenIssuer,
            RefreshTokenHasher refreshTokenHasher,
            AuthSessionStore authSessionStore,
            Clock clock
    ) {
        this.identityVerifiers =
                SocialIdentityVerifiers.toMap(identityVerifiers);

        this.socialAccountRepository =
                socialAccountRepository;
        this.userRepository =
                userRepository;
        this.emailNormalizer =
                emailNormalizer;
        this.emailLookupHasher =
                emailLookupHasher;
        this.socialSignupTokenIssuer =
                socialSignupTokenIssuer;
        this.tokenIssuer =
                tokenIssuer;
        this.refreshTokenHasher =
                refreshTokenHasher;
        this.authSessionStore =
                authSessionStore;
        this.clock =
                clock;
    }

    /**
     * Social Provider Credential을 검증하고
     * 기존 로그인 또는 신규 가입 필요 상태를 반환합니다.
     */
    @Transactional
    public SocialLoginResult login(
            SocialLoginCommand command
    ) {
        Objects.requireNonNull(
                command,
                "소셜 로그인 명령은 필수입니다."
        );

        SocialIdentityVerifier verifier =
                identityVerifiers.get(
                        command.provider()
                );

        if (verifier == null) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_PROVIDER_NOT_SUPPORTED
            );
        }

        VerifiedSocialIdentity identity =
                verifier.verify(
                        command.credential()
                );

        validateIdentity(
                command.provider(),
                identity
        );

        /*
         * 가장 먼저 provider + providerUserId로 조회합니다.
         *
         * 기존 소셜 사용자는 p_users에 동일 이메일이 당연히 존재하므로
         * 이메일 중복 검사를 먼저 하면 정상 로그인까지 차단됩니다.
         */
        SocialAccount socialAccount =
                socialAccountRepository
                        .findByProviderAndProviderUserId(
                                identity.provider(),
                                identity.providerUserId()
                        )
                        .orElse(null);

        if (socialAccount != null) {
            return authenticateExistingUser(
                    identity.provider(),
                    socialAccount
            );
        }

        /*
         * SocialAccount가 없는 신규 소셜 인증에 대해서만
         * 이메일 중복을 검사합니다.
         */
        String normalizedEmail =
                emailNormalizer.normalize(
                        identity.email()
                );

        String emailLookupHash =
                emailLookupHasher.hash(
                        normalizedEmail
                );

        /*
         * 일반 회원가입 또는 다른 방식으로 이미 가입된 이메일이면
         * 절대 SocialAccount를 자동 연결하지 않습니다.
         */
        if (
                userRepository.existsByEmailLookupHash(
                        emailLookupHash
                )
        ) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_SIGNUP_EMAIL_ALREADY_EXISTS
            );
        }

        VerifiedSocialIdentity normalizedIdentity =
                new VerifiedSocialIdentity(
                        identity.provider(),
                        identity.providerUserId(),
                        normalizedEmail,
                        true
                );

        String socialSignupToken =
                socialSignupTokenIssuer.issue(
                        normalizedIdentity
                );

        return SocialLoginResult.signupRequired(
                identity.provider(),
                socialSignupToken,
                normalizedEmail
        );
    }

    /**
     * 기존 SocialAccount에 연결된 사용자의
     * MAESAMCO 인증 세션을 생성합니다.
     */
    private SocialLoginResult authenticateExistingUser(
            SocialProvider provider,
            SocialAccount socialAccount
    ) {
        User user =
                userRepository
                        .findByIdForUpdate(
                                socialAccount.getUserId()
                        )
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.USER_NOT_ACTIVE
                                )
                        );

        user.assertActive();

        UUID sessionId =
                UUID.randomUUID();

        UUID familyId =
                UUID.randomUUID();

        Instant sessionStartedAt =
                clock.instant();

        IssuedTokens issuedTokens =
                tokenIssuer.issueTokens(
                        user.getId(),
                        user.getRole(),
                        sessionId
                );

        String refreshTokenHash =
                refreshTokenHasher.hash(
                        issuedTokens.refreshToken()
                );

        AuthSession authSession =
                new AuthSession(
                        sessionId,
                        familyId,
                        user.getId(),
                        refreshTokenHash,
                        sessionStartedAt,
                        issuedTokens.refreshTokenExpiresAt()
                );

        authSessionStore.save(
                authSession
        );

        Instant now =
                clock.instant();

        long accessTokenExpiresIn =
                TokenExpirationCalculator.remainingSeconds(
                        now,
                        issuedTokens.accessTokenExpiresAt()
                );

        return SocialLoginResult.authenticated(
                provider,
                user,
                issuedTokens,
                accessTokenExpiresIn
        );
    }

    /**
     * Provider Verifier가 반환한 결과가
     * 요청 Provider와 일치하고 이메일 인증이 완료됐는지 확인합니다.
     */
    private void validateIdentity(
            SocialProvider requestedProvider,
            VerifiedSocialIdentity identity
    ) {
        if (
                identity == null
                        || identity.provider()
                        != requestedProvider
        ) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }

        if (!identity.emailVerified()) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_EMAIL_NOT_VERIFIED
            );
        }
    }
}
