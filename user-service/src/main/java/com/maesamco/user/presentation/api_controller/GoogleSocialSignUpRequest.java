package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.SocialSignUpCommand;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Google 소셜 신규 회원가입 완료 요청입니다(#308).
 *
 * <p>이메일과 Google 사용자 식별자는 받지 않습니다.
 * 서버는 socialSignupToken에 귀속된, Google이 검증한 정보만 사용합니다.
 * 프로필 입력 규칙은 일반 회원가입({@code SignUpCommand})과 동일합니다.</p>
 */
public record GoogleSocialSignUpRequest(

        @Schema(
                description = "Google 소셜 로그인 SIGNUP_REQUIRED 응답으로 받은 일회성 가입 Token",
                maxLength = 256,
                accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "소셜 회원가입 Token은 필수입니다.")
        @Size(
                max = 256,
                message = "소셜 회원가입 Token은 256자 이하여야 합니다."
        )
        String socialSignupToken,

        @Schema(
                description = "서비스에서 사용할 닉네임 (한글, 영문, 숫자만 허용)",
                minLength = 2,
                maxLength = 20,
                pattern = "^[가-힣A-Za-z0-9]+$",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(
                min = 2,
                max = 20,
                message = "닉네임은 2자 이상 20자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^[가-힣A-Za-z0-9]+$",
                message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다."
        )
        String nickname,

        @Schema(
                description = "Java 학습 경험 개월 수",
                minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Java 경험 개월 수는 필수입니다.")
        @PositiveOrZero(
                message = "Java 경험 개월 수는 0 이상이어야 합니다."
        )
        Integer javaExperienceMonths,

        @Schema(
                description = "Java 학습 수준",
                allowableValues = {"BEGINNER", "BASIC"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "학습 수준은 필수입니다.")
        LearningLevel learningLevel
) {

    /**
     * 일반 회원가입과 동일하게 닉네임 앞뒤 공백을 제거합니다.
     */
    public GoogleSocialSignUpRequest {
        if (nickname != null) {
            nickname = nickname.trim();
        }
    }

    public SocialSignUpCommand toCommand() {
        return new SocialSignUpCommand(
                SocialProvider.GOOGLE,
                socialSignupToken,
                nickname,
                javaExperienceMonths,
                learningLevel
        );
    }

    /**
     * 소셜 회원가입 Token이 로그에 노출되지 않도록 보호합니다.
     */
    @Override
    public String toString() {
        return "GoogleSocialSignUpRequest[socialSignupToken=[PROTECTED]]";
    }
}
