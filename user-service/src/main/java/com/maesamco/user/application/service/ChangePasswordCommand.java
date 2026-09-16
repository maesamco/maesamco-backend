package com.maesamco.user.application.service;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 로그인 사용자의 비밀번호 변경 입력값입니다.
 *
 * <p>현재 비밀번호와 새 비밀번호는 민감정보이므로
 * 저장하거나 로그에 기록해서는 안 됩니다.</p>
 *
 * @param currentPassword 현재 비밀번호 원문
 * @param newPassword 새 비밀번호 원문
 */
public record ChangePasswordCommand(

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
        String currentPassword,

        @Schema(
                description = "새 비밀번호",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                minLength = 8,
                maxLength = 64
        )
        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(
                min = 8,
                max = 64,
                message = "새 비밀번호는 8자 이상 64자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+$",
                message = "새 비밀번호는 영문 대문자, 소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다."
        )
        String newPassword
) {

    /**
     * 로그 등에 비밀번호 원문이 노출되지 않도록 마스킹합니다.
     */
    @Override
    public String toString() {
        return "ChangePasswordCommand[sensitiveValues=[PROTECTED]]";
    }
}
