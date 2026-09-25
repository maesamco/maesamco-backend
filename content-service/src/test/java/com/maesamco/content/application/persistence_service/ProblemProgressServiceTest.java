package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.ProblemProgressFinder;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemProgressServiceTest {

    @Mock
    private ProblemProgressFinder problemProgressFinder;

    @Mock
    private ProblemProgressRepository problemProgressRepository;

    @InjectMocks
    private ProblemProgressService problemProgressService;

    @Test
    @DisplayName("사용자와 문제 ID를 기준으로 문제 풀이 이력을 단건 조회한다")
    void getProblemProgress() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        ProblemProgress problemProgress = org.mockito.Mockito.mock(ProblemProgress.class);

        when(problemProgressFinder.getByUserIdAndProblemIdOrigin(userId, problemId))
                .thenReturn(problemProgress);

        // when
        ProblemProgress result = problemProgressService.getProblemProgress(userId, problemId);

        // then
        assertSame(problemProgress, result);

        verify(problemProgressFinder).getByUserIdAndProblemIdOrigin(userId, problemId);
        verifyNoInteractions(problemProgressRepository);
    }

    @Test
    @DisplayName("단건 문제 풀이 이력이 존재하지 않으면 Finder에서 발생한 예외를 그대로 전달한다")
    void getProblemProgressNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        when(problemProgressFinder.getByUserIdAndProblemIdOrigin(userId, problemId))
                .thenThrow(new BusinessException(ErrorCode.PROBLEM_PROGRESS_NOT_FOUND));

        // when & then
        assertThrows(
                BusinessException.class,
                () -> problemProgressService.getProblemProgress(userId, problemId)
        );

        verify(problemProgressFinder).getByUserIdAndProblemIdOrigin(userId, problemId);
        verifyNoInteractions(problemProgressRepository);
    }

    @Test
    @DisplayName("progressStatus가 없으면 사용자의 전체 문제 풀이 이력을 페이징 조회한다")
    void getProblemProgressesWithoutProgressStatus() {
        // given
        UUID userId = UUID.randomUUID();
        PageQuery pageQuery = PageQuery.of(0, 20);

        ProblemProgress problemProgress1 = org.mockito.Mockito.mock(ProblemProgress.class);
        ProblemProgress problemProgress2 = org.mockito.Mockito.mock(ProblemProgress.class);

        PageResult<ProblemProgress> problemProgressPage = new PageResult<>(List.of(problemProgress1, problemProgress2), pageQuery.page(), pageQuery.size(), 2);

        when(problemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageQuery))
                .thenReturn(problemProgressPage);

        // when
        PageResult<ProblemProgress> result =
                problemProgressService.getProblemProgresses(userId, null, pageQuery);

        // then
        assertSame(problemProgressPage, result);
        assertEquals(2, result.content().size());
        assertEquals(2, result.totalElements());

        verify(problemProgressRepository)
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageQuery);

        verify(problemProgressRepository, never())
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                        userId,
                        ProblemProgressStatus.WRONG,
                        pageQuery
                );

        verify(problemProgressRepository, never())
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                        userId,
                        ProblemProgressStatus.CORRECT,
                        pageQuery
                );

        verifyNoInteractions(problemProgressFinder);
    }

    @Test
    @DisplayName("progressStatus가 WRONG이면 사용자의 WRONG 문제 풀이 이력만 페이징 조회한다")
    void getProblemProgressesWithWrongStatus() {
        // given
        UUID userId = UUID.randomUUID();
        PageQuery pageQuery = PageQuery.of(0, 20);

        ProblemProgress problemProgress1 = org.mockito.Mockito.mock(ProblemProgress.class);
        ProblemProgress problemProgress2 = org.mockito.Mockito.mock(ProblemProgress.class);

        PageResult<ProblemProgress> problemProgressPage = new PageResult<>(List.of(problemProgress1, problemProgress2), pageQuery.page(), pageQuery.size(), 2);

        when(problemProgressRepository.findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                userId,
                ProblemProgressStatus.WRONG,
                pageQuery
        )).thenReturn(problemProgressPage);

        // when
        PageResult<ProblemProgress> result =
                problemProgressService.getProblemProgresses(
                        userId,
                        ProblemProgressStatus.WRONG,
                        pageQuery
                );

        // then
        assertSame(problemProgressPage, result);
        assertEquals(2, result.content().size());
        assertEquals(2, result.totalElements());

        verify(problemProgressRepository)
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                        userId,
                        ProblemProgressStatus.WRONG,
                        pageQuery
                );

        verify(problemProgressRepository, never())
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageQuery);

        verifyNoInteractions(problemProgressFinder);
    }

    @Test
    @DisplayName("progressStatus가 CORRECT이면 사용자의 CORRECT 문제 풀이 이력만 페이징 조회한다")
    void getProblemProgressesWithCorrectStatus() {
        // given
        UUID userId = UUID.randomUUID();
        PageQuery pageQuery = PageQuery.of(0, 20);

        ProblemProgress problemProgress = org.mockito.Mockito.mock(ProblemProgress.class);

        PageResult<ProblemProgress> problemProgressPage = new PageResult<>(List.of(problemProgress), pageQuery.page(), pageQuery.size(), 1);

        when(problemProgressRepository.findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                userId,
                ProblemProgressStatus.CORRECT,
                pageQuery
        )).thenReturn(problemProgressPage);

        // when
        PageResult<ProblemProgress> result =
                problemProgressService.getProblemProgresses(
                        userId,
                        ProblemProgressStatus.CORRECT,
                        pageQuery
                );

        // then
        assertSame(problemProgressPage, result);
        assertEquals(1, result.content().size());
        assertEquals(1, result.totalElements());

        verify(problemProgressRepository)
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                        userId,
                        ProblemProgressStatus.CORRECT,
                        pageQuery
                );

        verify(problemProgressRepository, never())
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageQuery);

        verifyNoInteractions(problemProgressFinder);
    }

    @Test
    @DisplayName("문제 풀이 이력이 없으면 빈 Page를 반환한다")
    void getProblemProgressesEmpty() {
        // given
        UUID userId = UUID.randomUUID();
        PageQuery pageQuery = PageQuery.of(0, 20);

        PageResult<ProblemProgress> emptyPage = PageResult.empty(pageQuery);

        when(problemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageQuery))
                .thenReturn(emptyPage);

        // when
        PageResult<ProblemProgress> result =
                problemProgressService.getProblemProgresses(userId, null, pageQuery);

        // then
        assertSame(emptyPage, result);
        assertEquals(0, result.content().size());
        assertEquals(0, result.totalElements());

        verify(problemProgressRepository)
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageQuery);

        verifyNoInteractions(problemProgressFinder);
    }
}