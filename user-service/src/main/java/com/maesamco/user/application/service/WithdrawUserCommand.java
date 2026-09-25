package com.maesamco.user.application.service;

import com.maesamco.user.application.validation.ValidWithdrawCredential;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 로그인 사용자의 회원 탈퇴 입력값입니다.
 *
 * <p>본인 확인 수단은 둘 중 정확히 하나만 받습니다(#328).</p>
 *
 * <ul>
 *     <li>비밀번호로 가입한 사용자: {@code currentPassword}</li>
 *     <li>비밀번호 없이 소셜로 가입한 사용자: {@code googleIdToken} (Google 재인증)</li>
 * </ul>
 *
 * <p>비밀번호와 ID Token은 민감정보이므로 저장하거나
 * 로그에 기록해서는 안 됩니다.</p>
 *
 * @param currentPassword 현재 비밀번호 원문
 * @param googleIdToken Google 재인증으로 받은 ID Token
 */
@ValidWithdrawCredential
public record WithdrawUserCommand(

        @Schema(
                description = "현재 비밀번호 — 비밀번호로 가입한 사용자 (googleIdToken과 둘 중 하나만)",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                maxLength = 64
        )
        @Size(
                max = 64,
                message = "현재 비밀번호는 64자 이하여야 합니다."
        )
        String currentPassword,

        @Schema(
                description = "Google 재인증 ID Token — 비밀번호 없이 Google로 가입한 사용자 (currentPassword와 둘 중 하나만)",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                maxLength = 8192
        )
        @Size(
                max = 8192,
                message = "Google ID Token이 너무 깁니다."
        )
        String googleIdToken
) {

    /**
     * 비밀번호로 본인 확인하는 기존 탈퇴 요청을 생성합니다.
     */
    public WithdrawUserCommand(
            String currentPassword
    ) {
        this(currentPassword, null);
    }

    /**
     * Google 재인증으로 본인 확인하는 요청인지 확인합니다.
     */
    public boolean usesGoogleReauth() {
        return googleIdToken != null && !googleIdToken.isBlank();
    }

    /**
     * 로그 등에 비밀번호 원문과 ID Token이 노출되지 않도록 마스킹합니다.
     */
    @Override
    public String toString() {
        return "WithdrawUserCommand[sensitiveValues=[PROTECTED]]";
    }
}
