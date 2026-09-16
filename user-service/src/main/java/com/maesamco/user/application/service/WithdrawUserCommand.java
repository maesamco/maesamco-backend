package com.maesamco.user.application.service;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.maesamco.user.application.service.WithdrawUserCommand;

/**
 * 로그인 사용자의 회원 탈퇴 입력값입니다.
 *
 * <p>현재 비밀번호는 민감정보이므로 저장하거나
 * 로그에 기록해서는 안 됩니다.</p>
 *
 * @param currentPassword 현재 비밀번호 원문
 */
public record WithdrawUserCommand(

        @Schema(
                description = "현재 비밀번호",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                maxLength = 64
        )
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        @Size(
                max = 64,
                message = "현재 비밀번호는 64자 이하여야 합니다."
        )
        String currentPassword
) {

    /**
     * 로그 등에 비밀번호 원문이 노출되지 않도록 마스킹합니다.
     */
    @Override
    public String toString() {
        return "WithdrawUserCommand[sensitiveValues=[PROTECTED]]";
    }
}
