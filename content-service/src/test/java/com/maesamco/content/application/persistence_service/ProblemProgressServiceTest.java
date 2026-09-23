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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
        Pageable pageable = PageRequest.of(0, 20);

        ProblemProgress problemProgress1 = org.mockito.Mockito.mock(ProblemProgress.class);
        ProblemProgress problemProgress2 = org.mockito.Mockito.mock(ProblemProgress.class);

        Page<ProblemProgress> problemProgressPage =
                new PageImpl<>(List.of(problemProgress1, problemProgress2), pageable, 2);

        when(problemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable))
                .thenReturn(problemProgressPage);

        // when
        Page<ProblemProgress> result =
                problemProgressService.getProblemProgresses(userId, null, pageable);

        // then
        assertSame(problemProgressPage, result);
        assertEquals(2, result.getContent().size());
        assertEquals(2, result.getTotalElements());

        verify(problemProgressRepository)
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);

        verify(problemProgressRepository, never())
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                        userId,
                        ProblemProgressStatus.WRONG,
                        pageable
                );

        verify(problemProgressRepository, never())
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                        userId,
                        ProblemProgressStatus.CORRECT,
                        pageable
                );

        verifyNoInteractions(problemProgressFinder);
    }

    @Test
    @DisplayName("progressStatus가 WRONG이면 사용자의 WRONG 문제 풀이 이력만 페이징 조회한다")
    void getProblemProgressesWithWrongStatus() {
        // given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        ProblemProgress problemProgress1 = org.mockito.Mockito.mock(ProblemProgress.class);
        ProblemProgress problemProgress2 = org.mockito.Mockito.mock(ProblemProgress.class);

        Page<ProblemProgress> problemProgressPage =
                new PageImpl<>(List.of(problemProgress1, problemProgress2), pageable, 2);

        when(problemProgressRepository.findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                userId,
                ProblemProgressStatus.WRONG,
                pageable
        )).thenReturn(problemProgressPage);

        // when
        Page<ProblemProgress> result =
                problemProgressService.getProblemProgresses(
                        userId,
                        ProblemProgressStatus.WRONG,
                        pageable
                );

        // then
        assertSame(problemProgressPage, result);
        assertEquals(2, result.getContent().size());
        assertEquals(2, result.getTotalElements());

        verify(problemProgressRepository)
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                        userId,
                        ProblemProgressStatus.WRONG,
                        pageable
                );

        verify(problemProgressRepository, never())
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);

        verifyNoInteractions(problemProgressFinder);
    }

    @Test
    @DisplayName("progressStatus가 CORRECT이면 사용자의 CORRECT 문제 풀이 이력만 페이징 조회한다")
    void getProblemProgressesWithCorrectStatus() {
        // given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        ProblemProgress problemProgress = org.mockito.Mockito.mock(ProblemProgress.class);

        Page<ProblemProgress> problemProgressPage =
                new PageImpl<>(List.of(problemProgress), pageable, 1);

        when(problemProgressRepository.findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                userId,
                ProblemProgressStatus.CORRECT,
                pageable
        )).thenReturn(problemProgressPage);

        // when
        Page<ProblemProgress> result =
                problemProgressService.getProblemProgresses(
                        userId,
                        ProblemProgressStatus.CORRECT,
                        pageable
                );

        // then
        assertSame(problemProgressPage, result);
        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getTotalElements());

        verify(problemProgressRepository)
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                        userId,
                        ProblemProgressStatus.CORRECT,
                        pageable
                );

        verify(problemProgressRepository, never())
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);

        verifyNoInteractions(problemProgressFinder);
    }

    @Test
    @DisplayName("문제 풀이 이력이 없으면 빈 Page를 반환한다")
    void getProblemProgressesEmpty() {
        // given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        Page<ProblemProgress> emptyPage = Page.empty(pageable);

        when(problemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable))
                .thenReturn(emptyPage);

        // when
        Page<ProblemProgress> result =
                problemProgressService.getProblemProgresses(userId, null, pageable);

        // then
        assertSame(emptyPage, result);
        assertEquals(0, result.getContent().size());
        assertEquals(0, result.getTotalElements());

        verify(problemProgressRepository)
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);

        verifyNoInteractions(problemProgressFinder);
    }
}