package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.domain.dailyquiz.DailyQuizConceptCandidates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailyQuizConceptSlotSelectorTest {

    private final DailyQuizConceptSlotSelector selector = new DailyQuizConceptSlotSelector();

    @Test
    void 오답_정답_관심_개념_순서로_서로_다른_개념을_먼저_배치한다() {
        var candidates = DailyQuizConceptCandidates.fromProblemProgress(
                List.of("반복문"), List.of("조건문"), List.of("배열")
        );

        var slots = selector.select(candidates);

        assertThat(slots).isPresent();
        assertThat(slots.orElseThrow().values())
                .containsExactly("반복문", "조건문", "배열", "반복문", "반복문");
    }

    @Test
    void 개념이_전혀_없으면_공통_문제_폴백을_위해_빈_결과를_반환한다() {
        var candidates = DailyQuizConceptCandidates.fromProblemProgress(List.of(), List.of(), List.of());

        assertThat(selector.select(candidates)).isEmpty();
    }
}
