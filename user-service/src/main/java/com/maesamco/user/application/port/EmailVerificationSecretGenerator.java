package com.maesamco.user.application.port;

/**
 * 이메일 인증 과정에서 사용하는 보안 난수를 생성하는 Port입니다.
 *
 * <p>Application 계층은 {@code SecureRandom}과 같은
 * 구체적인 난수 생성 구현을 알지 않습니다.</p>
 *
 * <p>인증 코드는 사용자가 직접 입력하는 짧은 값이며,
 * 회원가입 인증 토큰은 충분한 엔트로피를 가진 일회성 비밀값으로 생성합니다.</p>
 */
public interface EmailVerificationSecretGenerator {

    /**
     * 사용자가 이메일에서 확인하여 입력할 인증 코드를 생성합니다.
     *
     * @return 이메일 인증 코드
     */
    String generateVerificationCode();

    /**
     * 이메일 인증 성공 후 회원가입에 사용할
     * 일회성 인증 토큰을 생성합니다.
     *
     * @return 회원가입 인증 토큰
     */
    String generateSignupToken();
}
