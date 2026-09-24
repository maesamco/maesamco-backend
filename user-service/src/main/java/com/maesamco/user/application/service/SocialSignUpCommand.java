package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.SocialProvider;

import java.util.Objects;

/**
 * 소셜 신규 회원가입 완료 애플리케이션 서비스에 전달하는 입력값입니다(#308).
 *
 * <p>이메일과 Provider 사용자 ID는 클라이언트 입력을 받지 않고
 * socialSignupToken에 귀속된 서버 측 정보만 사용합니다.</p>
 *
 * @param provider 가입을 완료할 소셜 인증 제공자
 * @param socialSignupToken 소셜 로그인 SIGNUP_REQUIRED 응답으로 받은 일회성 Token
 * @param nickname 사용자 닉네임
 * @param javaExperienceMonths Java 경험 개월 수
 * @param learningLevel Java 학습 수준
 */
public record SocialSignUpCommand(
        SocialProvider provider,
        String socialSignupToken,
        String nickname,
        Integer javaExperienceMonths,
        LearningLevel learningLevel
) {

    public SocialSignUpCommand {
        Objects.requireNonNull(
                provider,
                "소셜 로그인 Provider는 필수입니다."
        );

        if (
                socialSignupToken == null
                        || socialSignupToken.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "소셜 회원가입 Token은 필수입니다."
            );
        }

        if (nickname != null) {
            nickname = nickname.trim();
        }
    }

    /**
     * 소셜 회원가입 Token이 로그에 노출되지 않도록 보호합니다.
     */
    @Override
    public String toString() {
        return "SocialSignUpCommand[provider="
                + provider
                + ", socialSignupToken=[PROTECTED]]";
    }
}
