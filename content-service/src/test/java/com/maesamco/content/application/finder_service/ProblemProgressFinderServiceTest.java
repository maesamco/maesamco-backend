package com.maesamco.content.application.finder_service;

import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemProgressFinderServiceTest {

    @Mock
    private ProblemFinderService problemFinderService;

    @Mock
    private ProblemProgressRepository problemProgressRepository;

    @Mock
    private Problem problem;

    private ProblemProgressFinderService problemProgressFinderService;

    private UUID userId;
    private UUID problemId;

    @BeforeEach
    void setUp() {
        problemProgressFinderService = new ProblemProgressFinderService(
                problemFinderService,
                problemProgressRepository
        );

        userId = UUID.randomUUID();
        problemId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Problem이 존재하고 ProblemProgress가 있으면 정상 조회한다")
    void getByUserIdAndProblemId_success() {
        // given
        ProblemProgress progress = createProblemProgress();

        when(problemFinderService.getById(problemId))
                .thenReturn(problem);

        when(problem.getId())
                .thenReturn(problemId);

        when(problemProgressRepository.findByUserIdAndProblemId(userId, problemId))
                .thenReturn(Optional.of(progress));

        // when
        Optional<ProblemProgress> result =
                problemProgressFinderService.getByUserIdAndProblemId(
                        userId,
                        problemId
                );

        // then
        assertThat(result).containsSame(progress);

        verify(problemFinderService)
                .getById(problemId);

        verify(problemProgressRepository)
                .findByUserIdAndProblemId(userId, problemId);
    }

    @Test
    @DisplayName("Problem은 존재하지만 ProblemProgress가 없으면 Optional.empty를 반환한다")
    void getByUserIdAndProblemId_progressNotFound_returnsEmpty() {
        // given
        when(problemFinderService.getById(problemId))
                .thenReturn(problem);

        when(problem.getId())
                .thenReturn(problemId);

        when(problemProgressRepository.findByUserIdAndProblemId(userId, problemId))
                .thenReturn(Optional.empty());

        // when
        Optional<ProblemProgress> result =
                problemProgressFinderService.getByUserIdAndProblemId(
                        userId,
                        problemId
                );

        // then
        assertThat(result).isEmpty();

        verify(problemFinderService)
                .getById(problemId);

        verify(problemProgressRepository)
                .findByUserIdAndProblemId(userId, problemId);
    }

    @Test
    @DisplayName("ProblemProgress 조회 전에 Problem 존재 여부를 검증한다")
    void getByUserIdAndProblemId_validatesProblemFirst() {
        // given
        when(problemFinderService.getById(problemId))
                .thenReturn(problem);

        when(problem.getId())
                .thenReturn(problemId);

        when(problemProgressRepository.findByUserIdAndProblemId(userId, problemId))
                .thenReturn(Optional.empty());

        // when
        problemProgressFinderService.getByUserIdAndProblemId(
                userId,
                problemId
        );

        // then
        InOrder inOrder = inOrder(
                problemFinderService,
                problemProgressRepository
        );

        inOrder.verify(problemFinderService)
                .getById(problemId);

        inOrder.verify(problemProgressRepository)
                .findByUserIdAndProblemId(userId, problemId);
    }

    @Test
    @DisplayName("존재하지 않는 Problem이면 ProblemProgress를 조회하지 않고 예외를 전파한다")
    void getByUserIdAndProblemId_problemNotFound_throwsException() {
        // given
        when(problemFinderService.getById(problemId))
                .thenThrow(
                        new BusinessException(
                                ErrorCode.PROBLEM_NOT_FOUND
                        )
                );

        // when & then
        assertThatThrownBy(() ->
                problemProgressFinderService.getByUserIdAndProblemId(
                        userId,
                        problemId
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception)
                                .getErrorCode()
                )
                .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);

        verify(problemFinderService)
                .getById(problemId);

        verify(problemProgressRepository, never())
                .findByUserIdAndProblemId(
                        userId,
                        problemId
                );
    }

    @Test
    @DisplayName("요청받은 userId와 Problem의 id를 Progress 조회에 정확히 전달한다")
    void getByUserIdAndProblemId_passesExactIds() {
        // given
        UUID requestedUserId = UUID.randomUUID();
        UUID requestedProblemId = UUID.randomUUID();

        when(problemFinderService.getById(requestedProblemId))
                .thenReturn(problem);

        when(problem.getId())
                .thenReturn(requestedProblemId);

        when(problemProgressRepository.findByUserIdAndProblemId(
                requestedUserId,
                requestedProblemId
        )).thenReturn(Optional.empty());

        // when
        problemProgressFinderService.getByUserIdAndProblemId(
                requestedUserId,
                requestedProblemId
        );

        // then
        verify(problemFinderService)
                .getById(requestedProblemId);

        verify(problem)
                .getId();

        verify(problemProgressRepository)
                .findByUserIdAndProblemId(
                        requestedUserId,
                        requestedProblemId
                );
    }

    private ProblemProgress createProblemProgress() {
        return ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.WRONG,
                Instant.parse("2026-09-23T01:00:00Z")
        );
    }
}