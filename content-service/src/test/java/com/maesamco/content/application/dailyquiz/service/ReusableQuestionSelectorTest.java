package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionSelectionResult;
import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.SHORT_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;

class ReusableQuestionSelectorTest {

    private final ReusableQuestionSelector selector = new ReusableQuestionSelector();

    @Test
    void 같은_개념이_여러_슬롯에_있으면_서로_다른_문항을_배정한다() {
        QuestionSlots questionSlots = sameConceptQuestionSlots();
        DailyQuizQuestion firstQuestion = question(1, "반복문");
        DailyQuizQuestion secondQuestion = question(2, "반복문");
        DailyQuizQuestion thirdQuestion = question(3, "반복문");
        DailyQuizQuestion fourthQuestion = question(4, "반복문");
        DailyQuizQuestion fifthQuestion = question(5, "반복문");

        DailyQuizQuestionSelectionResult result = selector.select(
                questionSlots,
                List.of(firstQuestion, secondQuestion, thirdQuestion, fourthQuestion, fifthQuestion)
        );

        assertThat(result.selectedQuestionsBySlot()).hasSize(5);
        assertThat(result.selectedQuestionsBySlot().values())
                .containsExactlyInAnyOrder(
                        firstQuestion,
                        secondQuestion,
                        thirdQuestion,
                        fourthQuestion,
                        fifthQuestion
                );
        assertThat(result.missingQuestionSlotsByIndex()).isEmpty();
    }

    @Test
    void 다중_개념_문항을_다른_슬롯으로_옮겨_더_많은_슬롯을_채운다() {
        DailyQuizQuestion multipleConceptQuestion = question(1, "반복문", "조건문");
        DailyQuizQuestion loopQuestion = question(2, "반복문");
        DailyQuizQuestion arrayQuestion = question(3, "배열");
        DailyQuizQuestion stringQuestion = question(4, "문자열");
        DailyQuizQuestion methodQuestion = question(5, "메서드");
        QuestionSlots questionSlots = distinctConceptQuestionSlots();

        DailyQuizQuestionSelectionResult result = selector.select(
                questionSlots,
                List.of(
                        multipleConceptQuestion,
                        loopQuestion,
                        arrayQuestion,
                        stringQuestion,
                        methodQuestion
                )
        );

        assertThat(result.selectedQuestionsBySlot())
                .containsEntry(0, loopQuestion)
                .containsEntry(1, multipleConceptQuestion);
        assertThat(result.missingQuestionSlotsByIndex()).isEmpty();
    }

    @Test
    void 하나의_문항을_여러_슬롯에_중복_배정하지_않는다() {
        DailyQuizQuestion multipleConceptQuestion = question(1, "반복문", "조건문");
        QuestionSlots questionSlots = distinctConceptQuestionSlots();

        DailyQuizQuestionSelectionResult result = selector.select(
                questionSlots,
                List.of(multipleConceptQuestion)
        );

        assertThat(result.selectedQuestionsBySlot())
                .containsOnlyKeys(0)
                .containsValue(multipleConceptQuestion);
        assertThat(result.missingQuestionSlotsByIndex())
                .containsExactlyEntriesOf(Map.of(
                        1, questionSlots.at(1),
                        2, questionSlots.at(2),
                        3, questionSlots.at(3),
                        4, questionSlots.at(4)
                ));
    }

    private QuestionSlots sameConceptQuestionSlots() {
        return new QuestionSlots(List.of(
                new QuestionSlot("반복문", SHORT_ANSWER),
                new QuestionSlot("반복문", SHORT_ANSWER),
                new QuestionSlot("반복문", SHORT_ANSWER),
                new QuestionSlot("반복문", SHORT_ANSWER),
                new QuestionSlot("반복문", SHORT_ANSWER)
        ));
    }

    private QuestionSlots distinctConceptQuestionSlots() {
        return new QuestionSlots(List.of(
                new QuestionSlot("반복문", SHORT_ANSWER),
                new QuestionSlot("조건문", SHORT_ANSWER),
                new QuestionSlot("배열", SHORT_ANSWER),
                new QuestionSlot("문자열", SHORT_ANSWER),
                new QuestionSlot("메서드", SHORT_ANSWER)
        ));
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
