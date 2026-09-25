package com.maesamco.user.application.service;

/**
 * 인증 세션을 발급하는 흐름과, 그 흐름의 세션 저장 실패 처리 방식을 나타냅니다(#339).
 *
 * <ul>
 *     <li>로그인 계열: 저장 실패 예외를 그대로 전파합니다.</li>
 *     <li>회원가입 계열: 사용자는 이미 저장됐으므로 경고 로그를 남기고
 *     {@code SIGNUP_AUTO_LOGIN_FAILED}로 변환합니다. 클라이언트는 로그인으로 다시 시도할 수 있습니다.</li>
 * </ul>
 */
public enum AuthSessionPurpose {

    LOGIN("로그인", false),
    SOCIAL_LOGIN("소셜 로그인", false),
    SIGNUP("회원가입", true),
    SOCIAL_SIGNUP("소셜 회원가입", true);

    private final String description;
    private final boolean signupAutoLogin;

    AuthSessionPurpose(
            String description,
            boolean signupAutoLogin
    ) {
        this.description = description;
        this.signupAutoLogin = signupAutoLogin;
    }

    /**
     * 로그에 사용하는 흐름 이름입니다.
     */
    public String description() {
        return description;
    }

    /**
     * 회원가입 직후 자동 로그인 흐름인지 여부입니다.
     * true이면 세션 저장 실패를 {@code SIGNUP_AUTO_LOGIN_FAILED}로 변환합니다.
     */
    public boolean isSignupAutoLogin() {
        return signupAutoLogin;
    }
}
