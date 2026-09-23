package com.maesamco.user.application.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * 소셜 인증 처리 결과입니다.
 *
 * <p>기존 사용자는 AUTHENTICATED와 인증 정보를 반환하고,
 * 신규 사용자는 SIGNUP_REQUIRED와 Social Signup Token을 반환합니다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialLoginResult(
        SocialLoginStatus status,
        SocialProvider provider,

        UUID userId,
        String nickname,
        UserRole role,
        UserStatus userStatus,
        Integer javaExperienceMonths,
        LearningLevel learningLevel,
        String accessToken,
        Long accessTokenExpiresIn,

        String socialSignupToken,
        String email,

        IssuedTokens issuedTokens
) {

    public SocialLoginResult {
        Objects.requireNonNull(
                status,
                "소셜 로그인 상태는 필수입니다."
        );

        Objects.requireNonNull(
                provider,
                "소셜 로그인 Provider는 필수입니다."
        );
    }

    /**
     * 기존 소셜 사용자의 로그인 성공 결과를 생성합니다.
     */
    public static SocialLoginResult authenticated(
            SocialProvider provider,
            User user,
            IssuedTokens issuedTokens,
            long accessTokenExpiresIn
    ) {
        Objects.requireNonNull(
                user,
                "로그인 사용자는 필수입니다."
        );

        Objects.requireNonNull(
                issuedTokens,
                "발급된 Token은 필수입니다."
        );

        return new SocialLoginResult(
                SocialLoginStatus.AUTHENTICATED,
                provider,
                user.getId(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                user.getJavaExperienceMonths(),
                user.getLearningLevel(),
                issuedTokens.accessToken(),
                accessTokenExpiresIn,
                null,
                null,
                issuedTokens
        );
    }

    /**
     * 신규 소셜 사용자의 추가 회원가입 필요 결과를 생성합니다.
     */
    public static SocialLoginResult signupRequired(
            SocialProvider provider,
            String socialSignupToken,
            String email
    ) {
        if (
                socialSignupToken == null
                        || socialSignupToken.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Social Signup Token은 필수입니다."
            );
        }

        if (
                email == null
                        || email.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "소셜 인증 이메일은 필수입니다."
            );
        }

        return new SocialLoginResult(
                SocialLoginStatus.SIGNUP_REQUIRED,
                provider,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                socialSignupToken,
                email,
                null
        );
    }

    /**
     * Refresh Token을 포함한 내부 Token 정보는
     * 응답 JSON에 노출하지 않습니다.
     */
    @JsonIgnore
    public IssuedTokens issuedTokens() {
        return issuedTokens;
    }

    @Override
    public String toString() {
        return "SocialLoginResult[status="
                + status
                + ", provider="
                + provider
                + ", userId="
                + userId
                + ", accessToken=[PROTECTED]"
                + ", socialSignupToken=[PROTECTED]"
                + ", issuedTokens=[PROTECTED]]";
    }
}
