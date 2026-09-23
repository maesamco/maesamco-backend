package com.maesamco.user.application.port;

import com.maesamco.user.domain.entity.SocialProvider;

import java.util.Objects;

/**
 * 외부 Social Provider의 인증을 완료한 사용자 정보입니다.
 *
 * <p>providerUserId는 클라이언트 입력값을 신뢰하지 않고
 * Provider가 발급한 검증된 토큰에서 추출해야 합니다.</p>
 */
public record VerifiedSocialIdentity(
        SocialProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified
) {

    public VerifiedSocialIdentity {
        Objects.requireNonNull(
                provider,
                "소셜 로그인 Provider는 필수입니다."
        );

        providerUserId =
                requireText(
                        providerUserId,
                        "소셜 사용자 식별자는 필수입니다."
                );

        email =
                requireText(
                        email,
                        "소셜 인증 이메일은 필수입니다."
                );
    }

    private static String requireText(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return "VerifiedSocialIdentity[provider="
                + provider
                + ", providerUserId=[PROTECTED]"
                + ", email=[PROTECTED]"
                + ", emailVerified="
                + emailVerified
                + "]";
    }
}
