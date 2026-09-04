package com.maesamco.user.infrastructure.security.password;

import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Argon2idPasswordHasher의 비밀번호 해시와 검증 단위 테스트입니다.
 */
class Argon2idPasswordHasherTest {

    private static final String RAW_PASSWORD = "Abcd1234!";

    private final PasswordHasher passwordHasher =
            new Argon2idPasswordHasher(
                    Argon2PasswordEncoder
                            .defaultsForSpringSecurity_v5_8()
            );

    @Test
    @DisplayName("비밀번호를 Argon2id로 해시하고 원문과 일치하는지 확인한다")
    void hash_andMatches_succeedsForSamePassword() {
        // when
        String passwordHash = passwordHasher.hash(RAW_PASSWORD);

        // then
        assertThat(passwordHash)
                .startsWith("$argon2id$");
        assertThat(passwordHasher.matches(
                RAW_PASSWORD,
                passwordHash
        )).isTrue();
    }

    @Test
    @DisplayName("같은 비밀번호도 Salt에 의해 서로 다른 해시가 생성된다")
    void hash_generatesDifferentHashesForSamePassword() {
        // when
        String firstHash = passwordHasher.hash(RAW_PASSWORD);
        String secondHash = passwordHasher.hash(RAW_PASSWORD);

        // then
        assertThat(firstHash)
                .isNotEqualTo(secondHash);
    }

    @Test
    @DisplayName("다른 비밀번호는 저장된 해시와 일치하지 않는다")
    void matches_returnsFalseForDifferentPassword() {
        // given
        String passwordHash = passwordHasher.hash(RAW_PASSWORD);

        // when
        boolean matches = passwordHasher.matches(
                "Different123!",
                passwordHash
        );

        // then
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("비밀번호의 앞뒤 공백을 제거하지 않는다")
    void hash_preservesLeadingAndTrailingWhitespace() {
        // given
        String passwordWithWhitespace = " " + RAW_PASSWORD + " ";

        // when
        String passwordHash =
                passwordHasher.hash(passwordWithWhitespace);

        // then
        assertThat(passwordHasher.matches(
                passwordWithWhitespace,
                passwordHash
        )).isTrue();
        assertThat(passwordHasher.matches(
                RAW_PASSWORD,
                passwordHash
        )).isFalse();
    }

    @Test
    @DisplayName("해시할 비밀번호가 null이거나 공백이면 예외가 발생한다")
    void hash_rejectsNullOrBlankPassword() {
        assertInvalidPassword(null);
        assertInvalidPassword("   ");
    }

    @Test
    @DisplayName("검증할 비밀번호나 해시가 유효하지 않으면 false를 반환한다")
    void matches_returnsFalseForInvalidInput() {
        assertThat(passwordHasher.matches(
                null,
                "password-hash"
        )).isFalse();
        assertThat(passwordHasher.matches(
                RAW_PASSWORD,
                null
        )).isFalse();
        assertThat(passwordHasher.matches(
                RAW_PASSWORD,
                "   "
        )).isFalse();
    }

    /**
     * 필수 비밀번호 검증 실패 시 오류 코드와 메시지를 확인합니다.
     */
    private void assertInvalidPassword(String rawPassword) {
        assertThatThrownBy(() -> passwordHasher.hash(rawPassword))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.INVALID_INPUT_VALUE
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "해시할 비밀번호는 필수입니다."
                                    );
                        }
                );
    }
}
