package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.maesamco.content.dailyquiz.domain.entity.DailyQuizProblemType.SHORT_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;

class ReusableQuestionSelectorTest {

    private final ReusableQuestionSelector selector = new ReusableQuestionSelector();

    @Test
    void 같은_개념이_여러_슬롯에_있으면_서로_다른_문항을_배정한다() {
        DailyQuizQuestion firstQuestion = question(1, "반복문");
        DailyQuizQuestion secondQuestion = question(2, "반복문");

        QuestionSelection result = selector.select(
                List.of("반복문", "반복문"),
                List.of(firstQuestion, secondQuestion)
        );

        assertThat(result.selectedQuestionsBySlot()).hasSize(2);
        assertThat(result.selectedQuestionsBySlot().values())
                .containsExactlyInAnyOrder(firstQuestion, secondQuestion);
        assertThat(result.missingConceptsBySlot()).isEmpty();
    }

    @Test
    void 다중_개념_문항을_다른_슬롯으로_옮겨_더_많은_슬롯을_채운다() {
        DailyQuizQuestion multipleConceptQuestion = question(1, "반복문", "조건문");
        DailyQuizQuestion loopQuestion = question(2, "반복문");

        QuestionSelection result = selector.select(
                List.of("반복문", "조건문"),
                List.of(multipleConceptQuestion, loopQuestion)
        );

        assertThat(result.selectedQuestionsBySlot())
                .containsEntry(0, loopQuestion)
                .containsEntry(1, multipleConceptQuestion);
        assertThat(result.missingConceptsBySlot()).isEmpty();
    }

    @Test
    void 하나의_문항을_여러_슬롯에_중복_배정하지_않는다() {
        DailyQuizQuestion multipleConceptQuestion = question(1, "반복문", "조건문");

        QuestionSelection result = selector.select(
                List.of("반복문", "조건문"),
                List.of(multipleConceptQuestion)
        );

        assertThat(result.selectedQuestionsBySlot())
                .containsOnlyKeys(0)
                .containsValue(multipleConceptQuestion);
        assertThat(result.missingConceptsBySlot())
                .containsExactlyEntriesOf(Map.of(1, "조건문"));
    }

    private DailyQuizQuestion question(long id, String... conceptTags) {
        DailyQuizQuestion question = DailyQuizQuestion.createNew(
                SHORT_ANSWER,
                "테스트 질문 " + id,
                null,
                "정답",
                null,
                List.of(conceptTags)
        );

        ReflectionTestUtils.setField(question, "id", new UUID(0L, id));
        return question;
    }
}
