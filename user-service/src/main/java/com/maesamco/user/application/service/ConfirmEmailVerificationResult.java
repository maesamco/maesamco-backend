package com.maesamco.user.application.service;

/**
 * 이메일 인증 코드 확인 성공 결과입니다.
 *
 * <p>회원가입 시 사용할 일회용 인증 토큰과
 * 해당 토큰의 유효 시간을 반환합니다.</p>
 *
 * <p>회원가입 인증 토큰은 민감정보이므로 {@link #toString()}에서
 * 원문이 노출되지 않도록 마스킹합니다.</p>
 *
 * @param signupToken 회원가입 시 제출할 일회용 이메일 인증 토큰
 * @param expiresInSeconds 토큰 만료까지 남은 시간(초)
 */
public record ConfirmEmailVerificationResult(
        String signupToken,
        long expiresInSeconds
) {

    /**
     * 로그 등에 회원가입 인증 토큰 원문이 노출되지 않도록 마스킹합니다.
     */
    @Override
    public String toString() {
        return "ConfirmEmailVerificationResult["
                + "signupToken=******, "
                + "expiresInSeconds="
                + expiresInSeconds
                + "]";
    }
}
