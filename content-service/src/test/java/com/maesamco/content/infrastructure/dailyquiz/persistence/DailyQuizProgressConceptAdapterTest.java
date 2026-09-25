package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DailyQuizProgressConceptAdapterTest {

    private final SpringDataDailyQuizProgressConceptRepository repository =
            mock(SpringDataDailyQuizProgressConceptRepository.class);
    private final DailyQuizProgressConceptAdapter adapter = new DailyQuizProgressConceptAdapter(repository);

    @Test
    void 현재_오답은_개념_태그만_조회한다() {
        UUID userId = UUID.randomUUID();
        when(repository.findConceptTagsByStatus(userId, ProblemProgressStatus.WRONG, TagAttribute.CONCEPT))
                .thenReturn(List.of("반복문"));

        assertThat(adapter.getWrongConceptTags(userId)).containsExactly("반복문");
    }

    @Test
    void 정답은_퀴즈_날짜_시작_전의_개념_태그만_조회한다() {
        UUID userId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-09-22T15:00:00Z");
        when(repository.findConceptTagsByStatusAndSolvedBefore(
                userId, ProblemProgressStatus.CORRECT, cutoff, TagAttribute.CONCEPT
        )).thenReturn(List.of("조건문"));

        assertThat(adapter.getCorrectConceptTagsBefore(userId, cutoff)).containsExactly("조건문");
    }
}
