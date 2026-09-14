package com.maesamco.user.infrastructure.security.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SecureRandomEmailVerificationSecretGenerator}가 생성하는
 * 이메일 인증 비밀값의 형식과 엔트로피 길이를 검증합니다.
 *
 * <p>난수의 실제 값이나 생성 결과의 고유성을 확률적으로 검증하지 않고,
 * 외부 계약인 인증 코드 형식과 회원가입 토큰의 바이트 길이만 확인합니다.</p>
 */
class SecureRandomEmailVerificationSecretGeneratorTest {

    private final SecureRandomEmailVerificationSecretGenerator generator =
            new SecureRandomEmailVerificationSecretGenerator();

    /**
     * 이메일 인증 코드는 사용자가 직접 입력할 수 있도록
     * 항상 6자리 숫자 형식으로 생성되어야 합니다.
     */
    @Test
    @DisplayName("이메일 인증 코드는 6자리 숫자로 생성한다")
    void generatesSixDigitVerificationCode() {
        // when
        String verificationCode =
                generator.generateVerificationCode();

        // then
        assertThat(verificationCode)
                .matches("\\d{6}");
    }

    /**
     * 회원가입 인증 토큰은 256비트 보안 난수를 사용하므로,
     * URL-safe Base64 디코딩 결과가 정확히 32바이트인지 검증합니다.
     */
    @Test
    @DisplayName("회원가입 인증 토큰은 256비트 난수로 생성한다")
    void generates256BitSignupToken() {
        // when
        String signupToken =
                generator.generateSignupToken();

        byte[] decodedToken =
                Base64.getUrlDecoder()
                        .decode(signupToken);

        // then
        assertThat(decodedToken)
                .hasSize(32);
    }

    /**
     * 회원가입 인증 토큰은 HTTP 요청 본문 등에서 안전하게 전달할 수 있도록
     * URL-safe Base64 형식이며 padding 문자를 포함하지 않아야 합니다.
     */
    @Test
    @DisplayName("회원가입 인증 토큰은 padding 없는 URL-safe Base64 형식이다")
    void generatesUrlSafeSignupTokenWithoutPadding() {
        // when
        String signupToken =
                generator.generateSignupToken();

        // then
        assertThat(signupToken)
                .matches("[A-Za-z0-9_-]+")
                .doesNotContain("=");
    }
}
