package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.domain.dailyquiz.DailyQuizConceptCandidates;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DailyQuizConceptSlotSelectorTest {

    private final DailyQuizConceptSlotSelector selector = new DailyQuizConceptSlotSelector();
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final LocalDate attemptDate = LocalDate.of(2026, 9, 26);

    @Test
    void 오답_정답_관심_개념_순서로_서로_다른_개념을_먼저_배치한다() {
        var candidates = DailyQuizConceptCandidates.fromProblemProgress(
                List.of("반복문"), List.of("조건문"), List.of("배열")
        );

        var slots = selector.select(candidates, userId, attemptDate);

        assertThat(slots).isPresent();
        assertThat(slots.orElseThrow().values())
                .containsExactly("반복문", "조건문", "배열", "반복문", "반복문");
    }

    @Test
    void 개념이_전혀_없으면_공통_문제_폴백을_위해_빈_결과를_반환한다() {
        var candidates = DailyQuizConceptCandidates.fromProblemProgress(List.of(), List.of(), List.of());

        assertThat(selector.select(candidates, userId, attemptDate)).isEmpty();
    }

    @Test
    void 사용자와_날짜가_같으면_재실행해도_같은_개념을_선택한다() {
        var candidates = DailyQuizConceptCandidates.fromProblemProgress(
                List.of("개념F", "개념E", "개념D", "개념C", "개념B", "개념A"), List.of()
        );
        var reordered = DailyQuizConceptCandidates.fromProblemProgress(
                List.of("개념A", "개념B", "개념C", "개념D", "개념E", "개념F"), List.of()
        );

        assertThat(selector.select(candidates, userId, attemptDate))
                .isEqualTo(selector.select(reordered, userId, attemptDate));
    }

    @Test
    void 날짜가_바뀌면_여섯_개념을_순환해서_선택한다() {
        var candidates = DailyQuizConceptCandidates.fromProblemProgress(
                List.of("개념A", "개념B", "개념C", "개념D", "개념E", "개념F"), List.of()
        );

        var today = selector.select(candidates, userId, attemptDate).orElseThrow().values();
        var tomorrow = selector.select(candidates, userId, attemptDate.plusDays(1)).orElseThrow().values();

        assertThat(today).hasSize(5).doesNotHaveDuplicates();
        assertThat(tomorrow).hasSize(5).doesNotHaveDuplicates();
        assertThat(today).isNotEqualTo(tomorrow);
        assertThat(java.util.stream.Stream.concat(today.stream(), tomorrow.stream()).distinct().toList())
                .containsExactlyInAnyOrder("개념A", "개념B", "개념C", "개념D", "개념E", "개념F");
    }
}
