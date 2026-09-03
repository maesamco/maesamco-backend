package com.maesamco.user.application.service;

import com.maesamco.user.application.validation.ValidSignUpPassword;
import com.maesamco.user.domain.entity.LearningLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 애플리케이션 서비스에 전달하는 입력값입니다.
 *
 * <p>이메일과 비밀번호는 아직 보호 처리되지 않은 원문이므로
 * 저장하거나 로그에 기록해서는 안 됩니다.</p>
 *
 * @param email 사용자 이메일 원문
 * @param password 사용자 비밀번호 원문
 * @param nickname 사용자 닉네임
 * @param javaExperienceMonths Java 경험 개월 수
 * @param learningLevel Java 학습 수준
 */
@ValidSignUpPassword
public record SignUpCommand(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

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

        @PositiveOrZero(
                message = "Java 경험 개월 수는 0 이상이어야 합니다."
        )
        int javaExperienceMonths,

        @NotNull(message = "학습 수준은 필수입니다.")
        LearningLevel learningLevel
) {

    /**
     * API 입력 정책에 따라 이메일과 닉네임의 앞뒤 공백을 제거합니다.
     *
     * <p>이메일 소문자 변환은 EmailNormalizer가 담당하며,
     * 비밀번호는 가공하지 않고 입력값 그대로 유지합니다.</p>
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
     * 이메일과 비밀번호가 로그에 노출되지 않도록 민감값을 숨깁니다.
     */
    @Override
    public String toString() {
        return "SignUpCommand[sensitiveValues=[PROTECTED]]";
    }
}
