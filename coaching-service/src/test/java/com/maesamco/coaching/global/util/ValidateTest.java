package com.maesamco.coaching.global.util;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateTest {

    @Test
    @DisplayName("null이 아니면 값을 그대로 반환한다")
    void requireNonNull_returnsValue_whenNotNull() {
        assertThat(Validate.requireNonNull("값", "필드")).isEqualTo("값");
    }

    @Test
    @DisplayName("null이면 INVALID_INPUT_VALUE 예외를 던진다")
    void requireNonNull_throws_whenNull() {
        assertThatThrownBy(() -> Validate.requireNonNull(null, "코칭 세션 ID"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                );
    }

    @Test
    @DisplayName("받침 있는 필드명은 '은'을 붙인다")
    void requireNonNull_usesEun_whenFieldNameHasFinalConsonant() {
        assertThatThrownBy(() -> Validate.requireNonNull(null, "AI 호출 목적"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getMessage()).isEqualTo("AI 호출 목적은 필수입니다.")
                );
    }

    @Test
    @DisplayName("받침 없는 필드명은 '는'을 붙인다")
    void requireNonNull_usesNeun_whenFieldNameHasNoFinalConsonant() {
        assertThatThrownBy(() -> Validate.requireNonNull(null, "코칭 세션 ID"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getMessage()).isEqualTo("코칭 세션 ID는 필수입니다.")
                );
    }

    @Test
    @DisplayName("공백이 아닌 문자열이면 값을 그대로 반환한다")
    void requireText_returnsValue_whenNotBlank() {
        assertThat(Validate.requireText("내용", "필드")).isEqualTo("내용");
    }

    @Test
    @DisplayName("null이거나 공백뿐이면 INVALID_INPUT_VALUE 예외를 던진다")
    void requireText_throws_whenNullOrBlank() {
        assertThatThrownBy(() -> Validate.requireText(null, "힌트 본문"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                );

        assertThatThrownBy(() -> Validate.requireText("   ", "힌트 본문"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                );
    }
}
