package com.maesamco.user.application.service;

/**
 * Refresh Token 재발급 서비스에 전달하는 입력값입니다.
 *
 * <p>Refresh Token은 HttpOnly Cookie에서 전달받는 민감한 인증정보이므로
 * 저장하거나 로그에 기록해서는 안 됩니다.</p>
 *
 * @param refreshToken 재발급 요청에 사용되는 Refresh Token 원문
 */
public record RefreshCommand(
        String refreshToken
) {

    /**
     * Refresh Token 원문이 로그에 노출되지 않도록 민감값을 숨깁니다.
     */
    @Override
    public String toString() {
        return "RefreshCommand[refreshToken=[PROTECTED]]";
    }
}
