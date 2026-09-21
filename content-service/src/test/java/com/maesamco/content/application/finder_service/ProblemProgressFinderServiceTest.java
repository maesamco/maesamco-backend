package com.maesamco.content.application.finder_service;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemProgressFinderServiceTest {

    @Mock
    private ProblemProgressRepository problemProgressRepository;

    private ProblemProgressFinderService problemProgressFinderService;

    private UUID userId;
    private UUID problemId;

    @BeforeEach
    void setUp() {
        problemProgressFinderService = new ProblemProgressFinderService(problemProgressRepository);

        userId = UUID.randomUUID();
        problemId = UUID.randomUUID();
    }

    @Test
    @DisplayName("사용자와 문제 ID로 ProblemProgress를 조회한다")
    void getByUserIdAndProblemId_exists_returnsProblemProgress() {
        // given
        ProblemProgress problemProgress = createProblemProgress(
                problemId,
                ProblemProgressStatus.WRONG,
                Instant.parse("2026-09-21T00:00:00Z")
        );

        when(problemProgressRepository.findByUserIdAndProblemId(userId, problemId))
                .thenReturn(Optional.of(problemProgress));

        // when
        Optional<ProblemProgress> result = problemProgressFinderService.getByUserIdAndProblemId(userId, problemId);

        // then
        assertThat(result).contains(problemProgress);

        verify(problemProgressRepository).findByUserIdAndProblemId(userId, problemId);
    }

    @Test
    @DisplayName("사용자와 문제 ID에 해당하는 ProblemProgress가 없으면 빈 Optional을 반환한다")
    void getByUserIdAndProblemId_notExists_returnsEmpty() {
        // given
        when(problemProgressRepository.findByUserIdAndProblemId(userId, problemId))
                .thenReturn(Optional.empty());

        // when
        Optional<ProblemProgress> result = problemProgressFinderService.getByUserIdAndProblemId(userId, problemId);

        // then
        assertThat(result).isEmpty();

        verify(problemProgressRepository).findByUserIdAndProblemId(userId, problemId);
    }

    @Test
    @DisplayName("사용자의 전체 ProblemProgress 목록을 조회한다")
    void getByUserId_returnsProblemProgressList() {
        // given
        ProblemProgress first = createProblemProgress(
                UUID.randomUUID(),
                ProblemProgressStatus.CORRECT,
                Instant.parse("2026-09-21T02:00:00Z")
        );

        ProblemProgress second = createProblemProgress(
                UUID.randomUUID(),
                ProblemProgressStatus.WRONG,
                Instant.parse("2026-09-21T01:00:00Z")
        );

        List<ProblemProgress> expected = List.of(first, second);

        when(problemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(expected);

        // when
        List<ProblemProgress> result = problemProgressFinderService.getByUserId(userId);

        // then
        assertThat(result).containsExactly(first, second);

        verify(problemProgressRepository).findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Test
    @DisplayName("사용자의 ProblemProgress가 없으면 빈 목록을 반환한다")
    void getByUserId_notExists_returnsEmptyList() {
        // given
        when(problemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(List.of());

        // when
        List<ProblemProgress> result = problemProgressFinderService.getByUserId(userId);

        // then
        assertThat(result).isEmpty();

        verify(problemProgressRepository).findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Test
    @DisplayName("사용자와 풀이 상태로 ProblemProgress 목록을 조회한다")
    void getByUserIdAndProgressStatus_returnsMatchingProblemProgressList() {
        // given
        ProblemProgressStatus progressStatus = ProblemProgressStatus.WRONG;

        ProblemProgress first = createProblemProgress(
                UUID.randomUUID(),
                progressStatus,
                Instant.parse("2026-09-21T02:00:00Z")
        );

        ProblemProgress second = createProblemProgress(
                UUID.randomUUID(),
                progressStatus,
                Instant.parse("2026-09-21T01:00:00Z")
        );

        List<ProblemProgress> expected = List.of(first, second);

        when(problemProgressRepository.findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus))
                .thenReturn(expected);

        // when
        List<ProblemProgress> result = problemProgressFinderService.getByUserIdAndProgressStatus(userId, progressStatus);

        // then
        assertThat(result).containsExactly(first, second);

        verify(problemProgressRepository).findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus);
    }

    @Test
    @DisplayName("해당 풀이 상태의 ProblemProgress가 없으면 빈 목록을 반환한다")
    void getByUserIdAndProgressStatus_notExists_returnsEmptyList() {
        // given
        ProblemProgressStatus progressStatus = ProblemProgressStatus.CORRECT;

        when(problemProgressRepository.findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus))
                .thenReturn(List.of());

        // when
        List<ProblemProgress> result = problemProgressFinderService.getByUserIdAndProgressStatus(userId, progressStatus);

        // then
        assertThat(result).isEmpty();

        verify(problemProgressRepository).findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus);
    }

    private ProblemProgress createProblemProgress(
            UUID targetProblemId,
            ProblemProgressStatus progressStatus,
            Instant judgedAt
    ) {
        return ProblemProgress.create(
                userId,
                targetProblemId,
                1,
                1,
                progressStatus,
                judgedAt
        );
    }
}