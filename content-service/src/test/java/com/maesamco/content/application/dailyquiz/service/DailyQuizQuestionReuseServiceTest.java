package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionSelectionResult;
import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizQuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.FILL_IN_BLANK;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.MULTIPLE_CHOICE;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.SHORT_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyQuizQuestionReuseServiceTest {

    @Mock
    private DailyQuizQuestionRepository questionRepository;

    @Mock
    private ReusableQuestionSelector questionSelector;

    @InjectMocks
    private DailyQuizQuestionReuseService reuseService;

    @Test
    void 같은_개념이_반복돼도_개념은_한_번만_조회하고_후보_제한은_전체_슬롯_수로_전달한다() {
        QuestionSlots questionSlots = new QuestionSlots(List.of(
                new QuestionSlot("반복문", MULTIPLE_CHOICE),
                new QuestionSlot("반복문", SHORT_ANSWER),
                new QuestionSlot("반복문", MULTIPLE_CHOICE),
                new QuestionSlot("반복문", SHORT_ANSWER),
                new QuestionSlot("반복문", FILL_IN_BLANK)
        ));
        DailyQuizQuestionSelectionResult expected = new DailyQuizQuestionSelectionResult(
                Map.of(),
                Map.of(
                        0, questionSlots.at(0),
                        1, questionSlots.at(1),
                        2, questionSlots.at(2),
                        3, questionSlots.at(3),
                        4, questionSlots.at(4)
                )
        );
        when(questionRepository.findActiveByAnyConcepts(List.of("반복문"), 5))
                .thenReturn(List.of());
        when(questionSelector.select(questionSlots, List.of())).thenReturn(expected);

        DailyQuizQuestionSelectionResult result = reuseService.selectReusableQuestions(questionSlots);

        assertThat(result).isSameAs(expected);
        verify(questionRepository).findActiveByAnyConcepts(List.of("반복문"), 5);
        verify(questionSelector).select(questionSlots, List.of());
    }
}
