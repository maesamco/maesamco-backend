package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HmacSha256EmailVerificationSecretHasher의
 * 이메일 인증 비밀값 해시 정책 단위 테스트입니다.
 */
class HmacSha256EmailVerificationSecretHasherTest {

    private static final String ENCRYPTION_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final String LOOKUP_HMAC_KEY =
            "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    private static final String VERIFICATION_HMAC_KEY =
            "MDAxMTIyMzM0NDU1NjY3Nzg4OTlhYWJiY2NkZGVlZmY=";

    private final HmacSha256EmailVerificationSecretHasher secretHasher =
            createSecretHasher(VERIFICATION_HMAC_KEY);

    @Test
    @DisplayName("같은 인증 코드에는 항상 같은 해시를 생성한다")
    void hashVerificationCode_returnsSameHashForSameCode() {
        // given
        String verificationCode = "123456";

        // when
        String firstHash =
                secretHasher.hashVerificationCode(
                        verificationCode
                );
        String secondHash =
                secretHasher.hashVerificationCode(
                        verificationCode
                );

        // then
        assertThat(firstHash)
                .isEqualTo(secondHash);
    }

    @Test
    @DisplayName("같은 회원가입 토큰에는 항상 같은 해시를 생성한다")
    void hashSignupToken_returnsSameHashForSameToken() {
        // given
        String signupToken =
                "signup-token-value";

        // when
        String firstHash =
                secretHasher.hashSignupToken(
                        signupToken
                );
        String secondHash =
                secretHasher.hashSignupToken(
                        signupToken
                );

        // then
        assertThat(firstHash)
                .isEqualTo(secondHash);
    }

    @Test
    @DisplayName("같은 원문도 인증 코드와 회원가입 토큰의 해시는 서로 다르다")
    void hash_separatesVerificationCodeAndSignupTokenDomains() {
        // given
        String sameSecret = "123456";

        // when
        String verificationCodeHash =
                secretHasher.hashVerificationCode(
                        sameSecret
                );
        String signupTokenHash =
                secretHasher.hashSignupToken(
                        sameSecret
                );

        // then
        assertThat(verificationCodeHash)
                .isNotEqualTo(signupTokenHash);
    }

    @Test
    @DisplayName("이메일 인증 비밀값 해시는 64자의 소문자 16진수 문자열이다")
    void hash_returnsLowercaseHexSha256Hash() {
        // when
        String hash =
                secretHasher.hashVerificationCode(
                        "123456"
                );

        // then
        assertThat(hash)
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("인증 코드가 null이거나 공백이면 예외가 발생한다")
    void hashVerificationCode_rejectsNullOrBlankCode() {
        assertInvalidVerificationCode(null);
        assertInvalidVerificationCode("   ");
    }

    @Test
    @DisplayName("회원가입 인증 토큰이 null이거나 공백이면 예외가 발생한다")
    void hashSignupToken_rejectsNullOrBlankToken() {
        assertInvalidSignupToken(null);
        assertInvalidSignupToken("   ");
    }

    @Test
    @DisplayName("이메일 인증 해시 키가 Base64 형식이 아니면 생성할 수 없다")
    void constructor_rejectsInvalidBase64Key() {
        assertThatThrownBy(
                () -> createSecretHasher("not-base64***")
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이메일 인증 해시 키는 올바른 Base64 값이어야 합니다."
                );
    }

    @Test
    @DisplayName("이메일 인증 해시 키가 32바이트가 아니면 생성할 수 없다")
    void constructor_rejectsInvalidKeyLength() {
        // given
        String shortKey =
                Base64.getEncoder()
                        .encodeToString(new byte[16]);

        // when & then
        assertThatThrownBy(
                () -> createSecretHasher(shortKey)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이메일 인증 해시 키는 32바이트여야 합니다."
                );
    }

    /**
     * 잘못된 인증 코드 입력의 오류 코드와 메시지를 확인합니다.
     */
    private void assertInvalidVerificationCode(
            String verificationCode
    ) {
        assertThatThrownBy(
                () -> secretHasher.hashVerificationCode(
                        verificationCode
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.INVALID_INPUT_VALUE
                                    );

                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "해시를 생성할 이메일 인증 코드는 필수입니다."
                                    );
                        }
                );
    }

    /**
     * 잘못된 회원가입 인증 토큰 입력의 오류 코드와 메시지를 확인합니다.
     */
    private void assertInvalidSignupToken(
            String signupToken
    ) {
        assertThatThrownBy(
                () -> secretHasher.hashSignupToken(
                        signupToken
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.INVALID_INPUT_VALUE
                                    );

                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "해시를 생성할 회원가입 인증 토큰은 필수입니다."
                                    );
                        }
                );
    }

    /**
     * 테스트 키를 사용하는 이메일 인증 비밀값 해시 구현체를 생성합니다.
     */
    private static HmacSha256EmailVerificationSecretHasher
    createSecretHasher(String verificationHmacKey) {
        EmailSecurityProperties properties =
                new EmailSecurityProperties(
                        ENCRYPTION_KEY,
                        LOOKUP_HMAC_KEY,
                        verificationHmacKey
                );

        return new HmacSha256EmailVerificationSecretHasher(
                properties
        );
    }
}
