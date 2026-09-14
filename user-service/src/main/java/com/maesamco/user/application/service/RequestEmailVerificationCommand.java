package com.maesamco.user.application.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이메일 인증 코드 발송을 요청하는 명령입니다.
 *
 * <p>사용자가 입력한 이메일은 앞뒤 공백을 제거한 뒤
 * Application Service에서 정규화 및 조회용 해시 생성을 수행합니다.</p>
 *
 * <p>이메일은 개인정보이므로 {@link #toString()}에서
 * 원문이 노출되지 않도록 마스킹합니다.</p>
 *
 * @param email 인증 코드를 받을 이메일
 */
public record RequestEmailVerificationCommand(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(
                max = 255,
                message = "이메일은 255자 이하여야 합니다."
        )
        String email

) {

    /**
     * 이메일 앞뒤의 불필요한 공백을 제거합니다.
     */
    public RequestEmailVerificationCommand {
        if (email != null) {
            email = email.trim();
        }
    }

    /**
     * 로그 등에 이메일 원문이 노출되지 않도록 마스킹합니다.
     */
    @Override
    public String toString() {
        return "RequestEmailVerificationCommand[email=******]";
    }
}
