package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HmacSha256EmailLookupHasher의 조회 해시 정책 단위 테스트입니다.
 */
class HmacSha256EmailLookupHasherTest {

    private static final String ENCRYPTION_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String LOOKUP_HMAC_KEY =
            "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    private final HmacSha256EmailLookupHasher emailLookupHasher =
            createEmailLookupHasher(LOOKUP_HMAC_KEY);

    @Test
    @DisplayName("같은 이메일에는 항상 같은 조회 해시를 생성한다")
    void hash_returnsSameHashForSameEmail() {
        // given
        String normalizedEmail = "learner@example.com";

        // when
        String firstHash =
                emailLookupHasher.hash(normalizedEmail);
        String secondHash =
                emailLookupHasher.hash(normalizedEmail);

        // then
        assertThat(firstHash)
                .isEqualTo(secondHash);
    }

    @Test
    @DisplayName("조회 해시는 64자의 소문자 16진수 문자열이다")
    void hash_returnsLowercaseHexSha256Hash() {
        // when
        String emailLookupHash =
                emailLookupHasher.hash("learner@example.com");

        // then
        assertThat(emailLookupHash)
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("서로 다른 이메일에는 다른 조회 해시를 생성한다")
    void hash_returnsDifferentHashForDifferentEmail() {
        // when
        String firstHash =
                emailLookupHasher.hash("first@example.com");
        String secondHash =
                emailLookupHasher.hash("second@example.com");

        // then
        assertThat(firstHash)
                .isNotEqualTo(secondHash);
    }

    @Test
    @DisplayName("비밀 키가 다르면 같은 이메일에도 다른 해시를 생성한다")
    void hash_dependsOnLookupHmacKey() {
        // given
        HmacSha256EmailLookupHasher anotherHasher =
                createEmailLookupHasher(ENCRYPTION_KEY);
        String normalizedEmail = "learner@example.com";

        // when
        String firstHash =
                emailLookupHasher.hash(normalizedEmail);
        String secondHash =
                anotherHasher.hash(normalizedEmail);

        // then
        assertThat(firstHash)
                .isNotEqualTo(secondHash);
    }

    @Test
    @DisplayName("해시를 생성할 이메일이 null이거나 공백이면 예외가 발생한다")
    void hash_rejectsNullOrBlankEmail() {
        assertInvalidEmail(null);
        assertInvalidEmail("   ");
    }

    @Test
    @DisplayName("조회 해시 키가 Base64 형식이 아니면 생성할 수 없다")
    void constructor_rejectsInvalidBase64Key() {
        assertThatThrownBy(
                () -> createEmailLookupHasher("not-base64***")
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이메일 조회 해시 키는 올바른 Base64 값이어야 합니다."
                );
    }

    @Test
    @DisplayName("조회 해시 키가 32바이트가 아니면 생성할 수 없다")
    void constructor_rejectsInvalidKeyLength() {
        // given
        String shortKey = Base64.getEncoder()
                .encodeToString(new byte[16]);

        // when & then
        assertThatThrownBy(
                () -> createEmailLookupHasher(shortKey)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이메일 조회 해시 키는 32바이트여야 합니다."
                );
    }

    /**
     * 필수 이메일 검증 실패 시 오류 코드와 메시지를 확인합니다.
     */
    private void assertInvalidEmail(String email) {
        assertThatThrownBy(() -> emailLookupHasher.hash(email))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.INVALID_INPUT_VALUE
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "조회 해시를 생성할 이메일은 필수입니다."
                                    );
                        }
                );
    }

    /**
     * 테스트 키를 사용하는 이메일 조회 해시 구현체를 생성합니다.
     */
    private static HmacSha256EmailLookupHasher
    createEmailLookupHasher(String lookupHmacKey) {
        EmailSecurityProperties properties =
                new EmailSecurityProperties(
                        ENCRYPTION_KEY,
                        lookupHmacKey
                );

        return new HmacSha256EmailLookupHasher(properties);
    }
}
