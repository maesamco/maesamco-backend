package com.maesamco.user.application.service;

import com.maesamco.user.application.validation.ValidSignUpPassword;
import com.maesamco.user.domain.entity.LearningLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 애플리케이션 서비스에 전달하는 입력값입니다.
 *
 * <p>이메일, 비밀번호, 회원가입 인증 토큰은 보호되지 않은 원문이므로
 * 저장하거나 로그에 기록해서는 안 됩니다.</p>
 *
 * @param email 사용자 이메일 원문
 * @param signupToken 이메일 인증 후 발급된 일회용 회원가입 인증 토큰
 * @param password 사용자 비밀번호 원문
 * @param nickname 사용자 닉네임
 * @param javaExperienceMonths Java 경험 개월 수
 * @param learningLevel Java 학습 수준
 */
@ValidSignUpPassword
public record SignUpCommand(

        @Schema(
                description = "로그인과 이메일 인증에 사용하는 이메일",
                format = "email",
                maxLength = 255,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @Schema(
                description = "이메일 인증 확인 API에서 발급받은 일회용 회원가입 토큰",
                maxLength = 256,
                accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "회원가입 인증 토큰은 필수입니다.")
        @Size(
                max = 256,
                message = "회원가입 인증 토큰은 256자 이하여야 합니다."
        )
        String signupToken,

        @Schema(
                description = "영문 대문자·소문자·숫자·특수문자를 포함한 비밀번호",
                format = "password",
                minLength = 8,
                maxLength = 64,
                accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(
                min = 8,
                max = 64,
                message = "비밀번호는 8자 이상 64자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+$",
                message = "비밀번호는 영문 대문자, 소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다."
        )
        String password,

        @Schema(
                description = "서비스에서 사용할 닉네임",
                minLength = 2,
                maxLength = 20,
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
     * API 입력 정책에 따라 이메일과 닉네임의 앞뒤 공백을 제거합니다.
     *
     * <p>이메일 소문자 변환은 EmailNormalizer가 담당하며,
     * 비밀번호와 회원가입 인증 토큰은 가공하지 않고 입력값 그대로 유지합니다.</p>
     */
    public SignUpCommand {
        if (email != null) {
            email = email.trim();
        }

        if (nickname != null) {
            nickname = nickname.trim();
        }
    }

    /**
     * 이메일, 비밀번호, 회원가입 인증 토큰이 로그에 노출되지 않도록
     * 민감값을 숨깁니다.
     */
    @Override
    public String toString() {
        return "SignUpCommand[sensitiveValues=[PROTECTED]]";
    }
}
