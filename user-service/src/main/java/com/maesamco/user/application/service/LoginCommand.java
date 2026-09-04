package com.maesamco.user.application.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 애플리케이션 서비스에 전달하는 입력값입니다.
 *
 * <p>이메일과 비밀번호는 인증에 사용되는 민감정보이므로
 * 저장하거나 로그에 기록해서는 안 됩니다.</p>
 *
 * @param email 사용자 이메일 원문
 * @param password 사용자 비밀번호 원문
 */
public record LoginCommand(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(max = 64, message = "비밀번호는 64자 이하여야 합니다.")
        String password
) {

    /**
     * API 입력 정책에 따라 이메일의 앞뒤 공백을 제거합니다.
     *
     * <p>이메일 소문자 변환은 EmailNormalizer가 담당하며,
     * 비밀번호는 인증값이므로 가공하지 않고 입력 그대로 유지합니다.</p>
     */
    public LoginCommand {
        if (email != null) {
            email = email.trim();
        }
    }

    /**
     * 이메일과 비밀번호가 로그에 노출되지 않도록 민감값을 숨깁니다.
     */
    @Override
    public String toString() {
        return "LoginCommand[sensitiveValues=[PROTECTED]]";
    }
}
