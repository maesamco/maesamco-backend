package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowUpQuestionTest {

    @Test
    @DisplayName("역질문을 생성하면 필드가 그대로 채워진다")
    void create_setsFields() {
        // given
        UUID explanationId = UUID.randomUUID();
        String questionText = "왜 이 자료구조를 선택하셨나요?";
        String category = "선택이유";

        // when
        FollowUpQuestion followUpQuestion = FollowUpQuestion.create(explanationId, questionText, category);

        // then
        assertThat(followUpQuestion.getExplanationId()).isEqualTo(explanationId);
        assertThat(followUpQuestion.getQuestionText()).isEqualTo(questionText);
        assertThat(followUpQuestion.getCategory()).isEqualTo(category);
    }

    @Test
    @DisplayName("분류가 null이어도 생성할 수 있다")
    void create_allowsNullCategory() {
        // when
        FollowUpQuestion followUpQuestion =
                FollowUpQuestion.create(UUID.randomUUID(), "질문 내용", null);

        // then
        assertThat(followUpQuestion.getCategory()).isNull();
    }

    @Test
    @DisplayName("설명 ID가 null이면 생성할 수 없다")
    void create_throwsWhenExplanationIdIsNull() {
        assertThatThrownBy(() -> FollowUpQuestion.create(null, "질문 내용", null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("질문 내용이 비어있으면 생성할 수 없다")
    void create_throwsWhenQuestionTextIsBlank() {
        assertThatThrownBy(() -> FollowUpQuestion.create(UUID.randomUUID(), "   ", null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("질문 내용이 null이면 생성할 수 없다")
    void create_throwsWhenQuestionTextIsNull() {
        assertThatThrownBy(() -> FollowUpQuestion.create(UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("분류가 30자면 생성할 수 있고, 31자면 생성할 수 없다")
    void create_validatesCategoryMaxLength() {
        String thirtyChars = "a".repeat(30);
        String thirtyOneChars = "a".repeat(31);

        assertThat(FollowUpQuestion.create(UUID.randomUUID(), "질문 내용", thirtyChars).getCategory())
                .isEqualTo(thirtyChars);

        assertThatThrownBy(() -> FollowUpQuestion.create(UUID.randomUUID(), "질문 내용", thirtyOneChars))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
