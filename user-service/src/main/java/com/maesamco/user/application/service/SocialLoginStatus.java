package com.maesamco.user.application.service;

/**
 * 소셜 인증 이후 클라이언트가 진행해야 하는 상태입니다.
 */
public enum SocialLoginStatus {

    /**
     * 이미 가입된 소셜 사용자이며 로그인이 완료된 상태입니다.
     */
    AUTHENTICATED,

    /**
     * 소셜 인증은 완료됐지만 MAESAMCO 회원가입이 필요한 상태입니다.
     */
    SIGNUP_REQUIRED
}
