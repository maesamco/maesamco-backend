package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EmailNormalizer의 이메일 정규화 정책 단위 테스트입니다.
 */
class EmailNormalizerTest {

    private final EmailNormalizer emailNormalizer =
            new EmailNormalizer();

    @Test
    @DisplayName("이메일의 앞뒤 공백을 제거하고 소문자로 변환한다")
    void normalize_trimsAndConvertsToLowercase() {
        // given
        String email = "  Learner@Example.COM  ";

        // when
        String normalizedEmail = emailNormalizer.normalize(email);

        // then
        assertThat(normalizedEmail)
                .isEqualTo("learner@example.com");
    }

    @Test
    @DisplayName("시스템 기본 로케일과 관계없이 이메일을 정규화한다")
    void normalize_usesRootLocale() {
        // given
        Locale originalLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            // when
            String normalizedEmail =
                    emailNormalizer.normalize("I@EXAMPLE.COM");

            // then
            assertThat(normalizedEmail)
                    .isEqualTo("i@example.com");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    @DisplayName("이메일이 null이거나 공백이면 예외가 발생한다")
    void normalize_rejectsNullOrBlankEmail() {
        assertInvalidEmail(null);
        assertInvalidEmail("   ");
    }

    /**
     * 필수 이메일 검증 실패 시 오류 코드와 메시지를 확인합니다.
     */
    private void assertInvalidEmail(String email) {
        assertThatThrownBy(() -> emailNormalizer.normalize(email))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.INVALID_INPUT_VALUE
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo("이메일은 필수입니다.");
                        }
                );
    }
}
