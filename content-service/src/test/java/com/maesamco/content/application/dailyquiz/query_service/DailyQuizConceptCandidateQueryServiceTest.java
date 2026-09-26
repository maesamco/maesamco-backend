package com.maesamco.content.application.dailyquiz.query_service;

import com.maesamco.content.application.dailyquiz.port.ConceptLookupPort;
import com.maesamco.content.application.dailyquiz.port.ProblemProgressConceptPort;
import com.maesamco.content.application.dailyquiz.port.UserInterestConceptPort;
import com.maesamco.content.application.dailyquiz.query.DailyQuizConceptCandidatesGetQuery;
import com.maesamco.content.domain.dailyquiz.DailyQuizConceptCandidates;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DailyQuizConceptCandidateQueryServiceTest {

    private final ProblemProgressConceptPort progressPort = mock(ProblemProgressConceptPort.class);
    private final UserInterestConceptPort interestPort = mock(UserInterestConceptPort.class);
    private final ConceptLookupPort lookupPort = mock(ConceptLookupPort.class);
    private final DailyQuizConceptCandidateQueryService service = new DailyQuizConceptCandidateQueryService(
            progressPort, interestPort, lookupPort,
            Clock.fixed(Instant.parse("2026-09-22T18:00:00Z"), ZoneId.of("Asia/Seoul"))
    );

    @Test
    void 풀이_이력이_없으면_관심_개념을_사용한다() {
        UUID userId = UUID.randomUUID();
        UUID interestId = UUID.randomUUID();
        when(interestPort.getInterestConceptIds(userId)).thenReturn(List.of(interestId));
        when(lookupPort.getConceptTags(List.of(interestId))).thenReturn(List.of("반복문"));

        DailyQuizConceptCandidates result = service.get(
                DailyQuizConceptCandidatesGetQuery.from(userId, LocalDate.of(2026, 9, 23))
        );

        assertThat(result).isEqualTo(DailyQuizConceptCandidates.fromInterests(List.of("반복문")));
        verify(progressPort, never()).getWrongConceptTags(userId);
        verify(progressPort, never()).getCorrectConceptTagsBefore(any(), any());
    }

    @Test
    void 풀이_이력_개념이_부족하면_관심_개념도_조회한다() {
        UUID userId = UUID.randomUUID();
        UUID interestId = UUID.randomUUID();
        when(progressPort.existsByUserId(userId)).thenReturn(true);
        when(progressPort.getWrongConceptTags(userId)).thenReturn(List.of("조건문"));
        Instant cutoff = Instant.parse("2026-09-22T15:00:00Z");
        when(progressPort.getCorrectConceptTagsBefore(userId, cutoff)).thenReturn(List.of("반복문"));
        when(interestPort.getInterestConceptIds(userId)).thenReturn(List.of(interestId));
        when(lookupPort.getConceptTags(List.of(interestId))).thenReturn(List.of("배열"));

        DailyQuizConceptCandidates result = service.get(
                DailyQuizConceptCandidatesGetQuery.from(userId, LocalDate.of(2026, 9, 23))
        );

        assertThat(result).isEqualTo(DailyQuizConceptCandidates.fromProblemProgress(
                List.of("조건문"), List.of("반복문"), List.of("배열")
        ));
        verify(progressPort).getCorrectConceptTagsBefore(userId, cutoff);
    }

    @Test
    void 풀이_이력이_있어도_출제_개념이_없으면_관심_개념으로_보충한다() {
        UUID userId = UUID.randomUUID();
        UUID interestId = UUID.randomUUID();
        when(progressPort.existsByUserId(userId)).thenReturn(true);
        when(interestPort.getInterestConceptIds(userId)).thenReturn(List.of(interestId));
        when(lookupPort.getConceptTags(List.of(interestId))).thenReturn(List.of("변수"));

        DailyQuizConceptCandidates result = service.get(
                DailyQuizConceptCandidatesGetQuery.from(userId, LocalDate.of(2026, 9, 23))
        );

        assertThat(result).isEqualTo(DailyQuizConceptCandidates.fromProblemProgress(
                List.of(), List.of(), List.of("변수")
        ));
    }

    @Test
    void 풀이_이력_개념이_충분하면_관심_개념은_조회하지_않는다() {
        UUID userId = UUID.randomUUID();
        when(progressPort.existsByUserId(userId)).thenReturn(true);
        when(progressPort.getWrongConceptTags(userId)).thenReturn(List.of("변수", "조건문", "반복문"));
        when(progressPort.getCorrectConceptTagsBefore(any(), any()))
                .thenReturn(List.of("배열", "함수"));

        DailyQuizConceptCandidates result = service.get(
                DailyQuizConceptCandidatesGetQuery.from(userId, LocalDate.of(2026, 9, 23))
        );

        assertThat(result.interestConcepts()).isEmpty();
        verifyNoInteractions(interestPort, lookupPort);
    }
}
