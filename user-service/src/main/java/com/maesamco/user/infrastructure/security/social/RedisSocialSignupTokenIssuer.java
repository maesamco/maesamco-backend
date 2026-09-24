package com.maesamco.user.infrastructure.security.social;

import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.EmailVerificationSecretGenerator;
import com.maesamco.user.application.port.EmailVerificationSecretHasher;
import com.maesamco.user.application.port.SocialSignupTicket;
import com.maesamco.user.application.port.SocialSignupTokenIssuer;
import com.maesamco.user.application.port.SocialSignupTokenStore;
import com.maesamco.user.application.port.VerifiedSocialIdentity;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 소셜 인증 완료 후 후속 회원가입에 사용할
 * 일회성 Social Signup Token을 발급합니다.
 *
 * <p>클라이언트에는 충분한 엔트로피의 원문 Token을 반환하고,
 * Redis에는 Token의 HMAC 해시를 Key로, Token에 귀속된 소셜 인증 정보를 Value로 저장합니다.
 * 이메일은 원문 대신 DB와 동일한 암호문과 조회 해시로만 저장합니다(#308).</p>
 */
@Component
public class RedisSocialSignupTokenIssuer
        implements SocialSignupTokenIssuer {

    private final EmailVerificationSecretGenerator
            secretGenerator;

    private final EmailVerificationSecretHasher
            secretHasher;

    private final EmailLookupHasher
            emailLookupHasher;

    private final EmailCipher emailCipher;

    private final SocialSignupTokenStore
            socialSignupTokenStore;

    private final SocialSignupProperties properties;

    public RedisSocialSignupTokenIssuer(
            EmailVerificationSecretGenerator secretGenerator,
            EmailVerificationSecretHasher secretHasher,
            EmailLookupHasher emailLookupHasher,
            EmailCipher emailCipher,
            SocialSignupTokenStore socialSignupTokenStore,
            SocialSignupProperties properties
    ) {
        this.secretGenerator =
                secretGenerator;
        this.secretHasher =
                secretHasher;
        this.emailLookupHasher =
                emailLookupHasher;
        this.emailCipher =
                emailCipher;
        this.socialSignupTokenStore =
                socialSignupTokenStore;
        this.properties =
                properties;
    }

    @Override
    public String issue(
            VerifiedSocialIdentity identity
    ) {
        Objects.requireNonNull(
                identity,
                "검증된 소셜 사용자 정보는 필수입니다."
        );

        /*
         * 기존 이메일 회원가입과 동일한
         * 256비트 URL-safe Token 생성기를 재사용합니다.
         */
        String signupToken =
                secretGenerator
                        .generateSignupToken();

        String signupTokenHash =
                secretHasher
                        .hashSignupToken(
                                signupToken
                        );

        /*
         * SocialLoginService에서 이미 정규화한 이메일이므로
         * 같은 HMAC 조회 해시 정책을 그대로 사용할 수 있습니다.
         */
        String emailLookupHash =
                emailLookupHasher.hash(
                        identity.email()
                );

        /*
         * 회원가입 완료 요청은 클라이언트가 보낸 이메일을 신뢰하지 않고
         * 이 Ticket의 신원 정보만 사용합니다.
         */
        SocialSignupTicket ticket =
                new SocialSignupTicket(
                        identity.provider(),
                        identity.providerUserId(),
                        emailLookupHash,
                        emailCipher.encrypt(
                                identity.email()
                        )
                );

        socialSignupTokenStore.save(
                signupTokenHash,
                ticket,
                properties.tokenTtl()
        );

        return signupToken;
    }
}
