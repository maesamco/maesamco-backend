package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizQuestionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.FILL_IN_BLANK;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.MULTIPLE_CHOICE;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.SHORT_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DailyQuizFallbackQuestionSelectorTest {

    private final DailyQuizQuestionRepository repository = mock(DailyQuizQuestionRepository.class);
    private final DailyQuizFallbackQuestionSelector selector = new DailyQuizFallbackQuestionSelector(repository);

    @Test
    void 개인화_문항과_중복되지_않게_목표_유형을_공통_문제로_채운다() {
        DailyQuizQuestion personalized = question(MULTIPLE_CHOICE);
        DailyQuizQuestion sharedCandidate = personalized;
        DailyQuizQuestion multipleChoice = question(MULTIPLE_CHOICE);
        DailyQuizQuestion shortAnswerOne = question(SHORT_ANSWER);
        DailyQuizQuestion shortAnswerTwo = question(SHORT_ANSWER);
        DailyQuizQuestion fillInBlank = question(FILL_IN_BLANK);
        when(repository.findActiveFallbackQuestions()).thenReturn(List.of(
                sharedCandidate, multipleChoice, shortAnswerOne, shortAnswerTwo, fillInBlank
        ));

        List<DailyQuizQuestion> result = selector.fill(
                UUID.randomUUID(), LocalDate.of(2026, 9, 26), List.of(personalized)
        );

        assertThat(result).hasSize(5);
        assertThat(result).extracting(DailyQuizQuestion::getId).doesNotHaveDuplicates();
        assertThat(result).filteredOn(question -> question.getProblemType() == MULTIPLE_CHOICE).hasSize(2);
        assertThat(result).filteredOn(question -> question.getProblemType() == SHORT_ANSWER).hasSize(2);
        assertThat(result).filteredOn(question -> question.getProblemType() == FILL_IN_BLANK).hasSize(1);
    }

    @Test
    void 공통_문제_재고가_부족하면_확보한_문항만_반환한다() {
        DailyQuizQuestion fallbackQuestion = question(MULTIPLE_CHOICE);
        when(repository.findActiveFallbackQuestions()).thenReturn(List.of(fallbackQuestion));

        List<DailyQuizQuestion> result = selector.fill(
                UUID.randomUUID(), LocalDate.of(2026, 9, 26), List.of()
        );

        assertThat(result).hasSize(1);
    }

    @Test
    void 이미_다섯_문항이_있으면_공통_문제를_조회하지_않는다() {
        List<DailyQuizQuestion> personalized = List.of(
                question(MULTIPLE_CHOICE), question(SHORT_ANSWER), question(MULTIPLE_CHOICE),
                question(SHORT_ANSWER), question(FILL_IN_BLANK)
        );

        assertThat(selector.fill(UUID.randomUUID(), LocalDate.of(2026, 9, 26), personalized))
                .containsExactlyElementsOf(personalized);
        verifyNoInteractions(repository);
    }

    private static DailyQuizQuestion question(DailyQuizProblemType type) {
        DailyQuizQuestion question = mock(DailyQuizQuestion.class);
        when(question.getId()).thenReturn(UUID.randomUUID());
        when(question.getProblemType()).thenReturn(type);
        return question;
    }
}
