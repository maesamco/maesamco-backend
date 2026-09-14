package com.maesamco.user.application.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이메일 인증 코드를 확인하기 위한 명령입니다.
 *
 * <p>이메일은 앞뒤 공백을 제거한 뒤 Application Service에서
 * 기존 이메일 정규화 정책을 적용합니다.</p>
 *
 * <p>인증 코드는 사용자가 입력하는 6자리 숫자이며,
 * 앞뒤 공백만 제거하고 값 자체는 변경하지 않습니다.</p>
 *
 * <p>이메일과 인증 코드는 민감정보이므로 {@link #toString()}에서
 * 원문이 노출되지 않도록 마스킹합니다.</p>
 *
 * @param email 인증을 요청한 이메일
 * @param verificationCode 이메일로 전달된 6자리 인증 코드
 */
public record ConfirmEmailVerificationCommand(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(
                max = 255,
                message = "이메일은 255자 이하여야 합니다."
        )
        String email,

        @NotBlank(message = "인증 코드는 필수입니다.")
        @Pattern(
                regexp = "\\d{6}",
                message = "인증 코드는 6자리 숫자여야 합니다."
        )
        String verificationCode

) {

    /**
     * 사용자 입력에서 앞뒤 공백만 제거합니다.
     */
    public ConfirmEmailVerificationCommand {
        if (email != null) {
            email = email.trim();
        }

        if (verificationCode != null) {
            verificationCode = verificationCode.trim();
        }
    }

    /**
     * 로그 등에 이메일과 인증 코드 원문이 노출되지 않도록 마스킹합니다.
     */
    @Override
    public String toString() {
        return "ConfirmEmailVerificationCommand["
                + "email=******, "
                + "verificationCode=******"
                + "]";
    }
}
