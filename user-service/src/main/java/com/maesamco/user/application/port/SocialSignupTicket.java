package com.maesamco.user.application.port;

import com.maesamco.user.domain.entity.SocialProvider;

import java.util.Objects;

/**
 * Social Signup Token에 귀속된 소셜 인증 정보입니다(#308).
 *
 * <p>소셜 로그인에서 Provider가 검증한 신원만 담으며,
 * 회원가입 완료 요청에서는 클라이언트 입력 대신 이 값을 신뢰합니다.</p>
 *
 * <p>이메일은 원문을 보관하지 않고 DB와 동일한 방식의 암호문과 조회 해시만 보관합니다.</p>
 *
 * @param provider 소셜 인증 제공자
 * @param providerUserId Provider가 보장하는 사용자 고유 ID (Google은 OIDC sub)
 * @param emailLookupHash 정규화 이메일의 HMAC 조회 해시
 * @param encryptedEmail 정규화 이메일의 AES-GCM 암호문
 */
public record SocialSignupTicket(
        SocialProvider provider,
        String providerUserId,
        String emailLookupHash,
        String encryptedEmail
) {

    public SocialSignupTicket {
        Objects.requireNonNull(
                provider,
                "소셜 로그인 Provider는 필수입니다."
        );

        providerUserId = requireText(
                providerUserId,
                "소셜 사용자 식별자는 필수입니다."
        );

        emailLookupHash = requireText(
                emailLookupHash,
                "이메일 조회 해시는 필수입니다."
        );

        encryptedEmail = requireText(
                encryptedEmail,
                "암호화 이메일은 필수입니다."
        );
    }

    private static String requireText(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    @Override
    public String toString() {
        return "SocialSignupTicket[provider="
                + provider
                + ", providerUserId=[PROTECTED]"
                + ", emailLookupHash=[PROTECTED]"
                + ", encryptedEmail=[PROTECTED]]";
    }
}
