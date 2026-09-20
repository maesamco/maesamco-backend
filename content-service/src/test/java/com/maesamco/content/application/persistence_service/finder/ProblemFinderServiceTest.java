package com.maesamco.content.application.persistence_service.finder;

import com.maesamco.content.application.finder_service.ProblemFinderService;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.repository.problem.ProblemCommandRepository;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemFinderServiceTest {

    @Mock
    private ProblemQueryRepository problemQueryRepository;

    @Mock
    private ProblemCommandRepository problemCommandRepository;

    @InjectMocks
    private ProblemFinderService problemFinderService;

    @Test
    @DisplayName("문제 ID로 문제를 조회한다")
    void getById_success() {
        // given
        UUID problemId = UUID.randomUUID();
        Problem problem = mock(Problem.class);

        when(problemQueryRepository.findById(problemId))
                .thenReturn(Optional.of(problem));

        // when
        Problem result = problemFinderService.getById(problemId);

        // then
        assertThat(result).isSameAs(problem);

        verify(problemQueryRepository).findById(problemId);
        verifyNoInteractions(problemCommandRepository);
    }

    @Test
    @DisplayName("존재하지 않는 문제를 조회하면 BusinessException이 발생한다")
    void getById_notFound_throwsException() {
        // given
        UUID problemId = UUID.randomUUID();

        when(problemQueryRepository.findById(problemId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> problemFinderService.getById(problemId))
                .isInstanceOf(BusinessException.class);

        verify(problemQueryRepository).findById(problemId);
        verifyNoInteractions(problemCommandRepository);
    }

    @Test
    @DisplayName("문제 ID로 문제를 비관적 잠금 조회한다")
    void lockById_success() {
        // given
        UUID problemId = UUID.randomUUID();
        Problem problem = mock(Problem.class);

        when(problemCommandRepository.findByIdForUpdate(problemId))
                .thenReturn(Optional.of(problem));

        // when
        problemFinderService.lockById(problemId);

        // then
        verify(problemCommandRepository).findByIdForUpdate(problemId);
        verifyNoInteractions(problemQueryRepository);
    }

    @Test
    @DisplayName("잠금 대상 문제가 존재하지 않으면 BusinessException이 발생한다")
    void lockById_notFound_throwsException() {
        // given
        UUID problemId = UUID.randomUUID();

        when(problemCommandRepository.findByIdForUpdate(problemId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> problemFinderService.lockById(problemId))
                .isInstanceOf(BusinessException.class);

        verify(problemCommandRepository).findByIdForUpdate(problemId);
        verifyNoInteractions(problemQueryRepository);
    }
}