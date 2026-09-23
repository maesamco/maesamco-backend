package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.SocialProvider;

import java.util.Objects;

/**
 * 소셜 로그인 애플리케이션 서비스에 전달하는 입력값입니다.
 *
 * <p>credential에는 Google ID Token 등 Provider 인증값이 들어가므로
 * 로그에 원문을 기록해서는 안 됩니다.</p>
 */
public record SocialLoginCommand(
        SocialProvider provider,
        String credential
) {

    public SocialLoginCommand {
        Objects.requireNonNull(
                provider,
                "소셜 로그인 Provider는 필수입니다."
        );

        if (
                credential == null
                        || credential.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "소셜 로그인 Credential은 필수입니다."
            );
        }
    }

    @Override
    public String toString() {
        return "SocialLoginCommand[provider="
                + provider
                + ", credential=[PROTECTED]]";
    }
}
