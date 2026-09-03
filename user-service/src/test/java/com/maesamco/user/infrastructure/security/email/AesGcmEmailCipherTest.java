package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AesGcmEmailCipher의 이메일 암호화 정책 단위 테스트입니다.
 */
class AesGcmEmailCipherTest {

    private static final String ENCRYPTION_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String LOOKUP_HMAC_KEY =
            "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";
    private static final String VERSION_PREFIX = "v1:";

    private final AesGcmEmailCipher emailCipher =
            createEmailCipher(ENCRYPTION_KEY);

    @Test
    @DisplayName("이메일을 암호화한 뒤 원문으로 복호화할 수 있다")
    void encryptAndDecrypt_restoresOriginalEmail() {
        // given
        String normalizedEmail = "learner@example.com";

        // when
        String encryptedEmail =
                emailCipher.encrypt(normalizedEmail);
        String decryptedEmail =
                emailCipher.decrypt(encryptedEmail);

        // then
        assertThat(encryptedEmail)
                .startsWith(VERSION_PREFIX)
                .doesNotContain(normalizedEmail);
        assertThat(decryptedEmail)
                .isEqualTo(normalizedEmail);
    }

    @Test
    @DisplayName("같은 이메일을 암호화해도 매번 다른 암호문을 생성한다")
    void encrypt_usesRandomIv() {
        // given
        String normalizedEmail = "learner@example.com";

        // when
        String firstEncryptedEmail =
                emailCipher.encrypt(normalizedEmail);
        String secondEncryptedEmail =
                emailCipher.encrypt(normalizedEmail);

        // then
        assertThat(firstEncryptedEmail)
                .isNotEqualTo(secondEncryptedEmail);
    }

    @Test
    @DisplayName("암호화할 이메일이 null이거나 공백이면 예외가 발생한다")
    void encrypt_rejectsNullOrBlankEmail() {
        assertInvalidEmail(null);
        assertInvalidEmail("   ");
    }

    @Test
    @DisplayName("변조된 이메일 암호문은 복호화할 수 없다")
    void decrypt_rejectsTamperedCipherText() {
        // given
        String encryptedEmail =
                emailCipher.encrypt("learner@example.com");
        byte[] payload = Base64.getDecoder().decode(
                encryptedEmail.substring(VERSION_PREFIX.length())
        );
        payload[payload.length - 1] ^= 1;

        String tamperedEmail = VERSION_PREFIX
                + Base64.getEncoder().encodeToString(payload);

        // when & then
        assertThatThrownBy(
                () -> emailCipher.decrypt(tamperedEmail)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이메일 복호화에 실패했습니다.");
    }

    @Test
    @DisplayName("지원하지 않는 버전의 암호문은 복호화할 수 없다")
    void decrypt_rejectsUnsupportedVersion() {
        assertThatThrownBy(
                () -> emailCipher.decrypt("v2:cipher-text")
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "지원하지 않는 이메일 암호문 형식입니다."
                );
    }

    @Test
    @DisplayName("암호화 키가 Base64 형식이 아니면 생성할 수 없다")
    void constructor_rejectsInvalidBase64Key() {
        assertThatThrownBy(
                () -> createEmailCipher("not-base64***")
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이메일 암호화 키는 올바른 Base64 값이어야 합니다."
                );
    }

    @Test
    @DisplayName("암호화 키가 32바이트가 아니면 생성할 수 없다")
    void constructor_rejectsInvalidKeyLength() {
        // given
        String shortKey = Base64.getEncoder()
                .encodeToString(new byte[16]);

        // when & then
        assertThatThrownBy(
                () -> createEmailCipher(shortKey)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이메일 암호화 키는 32바이트여야 합니다."
                );
    }

    /**
     * 필수 이메일 검증 실패 시 오류 코드와 메시지를 확인합니다.
     */
    private void assertInvalidEmail(String email) {
        assertThatThrownBy(() -> emailCipher.encrypt(email))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.INVALID_INPUT_VALUE
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "암호화할 이메일은 필수입니다."
                                    );
                        }
                );
    }

    /**
     * 테스트 키를 사용하는 이메일 암호화 구현체를 생성합니다.
     */
    private static AesGcmEmailCipher createEmailCipher(
            String encryptionKey
    ) {
        EmailSecurityProperties properties =
                new EmailSecurityProperties(
                        encryptionKey,
                        LOOKUP_HMAC_KEY
                );

        return new AesGcmEmailCipher(properties);
    }
}
