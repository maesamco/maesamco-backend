package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.domain.dailyquiz.ConceptSlots;
import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.FILL_IN_BLANK;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.MULTIPLE_CHOICE;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.SHORT_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyQuizQuestionSlotAllocatorTest {

    private final DailyQuizQuestionSlotAllocator allocator =
            new DailyQuizQuestionSlotAllocator();

    @Test
    void 개념_슬롯에_객관식_2개_단답형_2개_빈칸형_1개를_배정한다() {
        ConceptSlots conceptSlots = new ConceptSlots(List.of(
                "반복문",
                "조건문",
                "배열",
                "문자열",
                "메서드"
        ));

        QuestionSlots result = allocator.allocate(conceptSlots);

        assertThat(result.values())
                .extracting(QuestionSlot::conceptTag)
                .containsExactly("반복문", "조건문", "배열", "문자열", "메서드");
        assertThat(result.values())
                .extracting(QuestionSlot::problemType)
                .containsExactly(
                        MULTIPLE_CHOICE,
                        SHORT_ANSWER,
                        MULTIPLE_CHOICE,
                        SHORT_ANSWER,
                        FILL_IN_BLANK
                );
    }

    @Test
    void 개념_슬롯이_null이면_실패한다() {
        assertThatThrownBy(() -> allocator.allocate(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                )
                .hasMessage("개념 슬롯은 필수입니다.");
    }
}
