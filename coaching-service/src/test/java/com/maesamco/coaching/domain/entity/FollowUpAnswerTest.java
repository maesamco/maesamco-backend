package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowUpAnswerTest {

    @Test
    @DisplayName("답변을 생성하면 필드가 그대로 채워진다")
    void create_setsFields() {
        // given
        UUID followUpQuestionId = UUID.randomUUID();
        String answerText = "배열은 0부터 시작하니까 마지막 인덱스는 길이-1입니다.";

        // when
        FollowUpAnswer followUpAnswer = FollowUpAnswer.create(followUpQuestionId, answerText);

        // then
        assertThat(followUpAnswer.getFollowUpQuestionId()).isEqualTo(followUpQuestionId);
        assertThat(followUpAnswer.getAnswerText()).isEqualTo(answerText);
    }

    @Test
    @DisplayName("역질문 ID가 null이면 생성할 수 없다")
    void create_throwsWhenFollowUpQuestionIdIsNull() {
        assertThatThrownBy(() -> FollowUpAnswer.create(null, "답변 내용"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("답변 내용이 비어있으면 생성할 수 없다")
    void create_throwsWhenAnswerTextIsBlank() {
        assertThatThrownBy(() -> FollowUpAnswer.create(UUID.randomUUID(), "   "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("답변 내용이 null이면 생성할 수 없다")
    void create_throwsWhenAnswerTextIsNull() {
        assertThatThrownBy(() -> FollowUpAnswer.create(UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
