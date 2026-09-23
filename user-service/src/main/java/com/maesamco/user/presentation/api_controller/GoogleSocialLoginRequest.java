package com.maesamco.user.presentation.api_controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Google 소셜 로그인 요청입니다.
 *
 * <p>클라이언트가 Google 인증 후 전달받은 ID Token만 전달합니다.
 * providerUserId, 이메일 등의 사용자 정보는 클라이언트 입력을 신뢰하지 않고
 * 백엔드가 검증된 ID Token에서 직접 추출합니다.</p>
 *
 * @param idToken Google ID Token
 */
public record GoogleSocialLoginRequest(

        @NotBlank(
                message = "Google ID Token은 필수입니다."
        )
        @Size(
                max = 8192,
                message = "Google ID Token이 너무 깁니다."
        )
        String idToken
) {

    /**
     * ID Token이 로그에 노출되지 않도록 보호합니다.
     */
    @Override
    public String toString() {
        return "GoogleSocialLoginRequest[idToken=[PROTECTED]]";
    }
}
