package com.maesamco.user.application.port;

/**
 * 이메일 인증 과정에서 사용하는 비밀값을
 * 저장 가능한 단방향 해시로 변환합니다.
 *
 * <p>인증 코드와 회원가입 토큰은 서로 다른 용도로 사용되므로
 * 구현체는 두 값의 해시 도메인을 분리해야 합니다.</p>
 */
public interface EmailVerificationSecretHasher {

    /**
     * 이메일 인증 코드를 단방향 해시로 변환합니다.
     *
     * @param verificationCode 이메일 인증 코드
     * @return 저장 가능한 인증 코드 해시
     */
    String hashVerificationCode(String verificationCode);

    /**
     * 회원가입 인증 토큰을 단방향 해시로 변환합니다.
     *
     * @param signupToken 회원가입 인증 토큰
     * @return 저장 가능한 회원가입 토큰 해시
     */
    String hashSignupToken(String signupToken);
}
