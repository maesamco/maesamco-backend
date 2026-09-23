package com.maesamco.content.application.finder_service;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemTagFinderServiceTest {

    @Mock
    private ProblemFinderService problemFinderService;

    @Mock
    private ProblemTagRepository problemTagRepository;

    @Mock
    private Problem problem;

    @Mock
    private ProblemTag firstProblemTag;

    @Mock
    private ProblemTag secondProblemTag;

    @Mock
    private Tag firstTag;

    @Mock
    private Tag secondTag;

    private ProblemTagFinderService problemTagFinderService;

    private UUID problemId;
    private UUID resolvedProblemId;
    private UUID tagId;

    @BeforeEach
    void setUp() {
        problemTagFinderService = new ProblemTagFinderService(
                problemFinderService,
                problemTagRepository
        );

        problemId = UUID.randomUUID();
        resolvedProblemId = UUID.randomUUID();
        tagId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Problem과 ProblemTag가 존재하면 true를 반환한다")
    void existsByProblemIdAndTagId_exists_returnsTrue() {
        // given
        when(problemFinderService.getById(problemId)).thenReturn(problem);
        when(problem.getId()).thenReturn(resolvedProblemId);
        when(problemTagRepository.existsByProblemIdAndTagId(resolvedProblemId, tagId)).thenReturn(true);

        // when
        boolean result = problemTagFinderService.existsByProblemIdAndTagId(problemId, tagId);

        // then
        assertThat(result).isTrue();

        verify(problemFinderService).getById(problemId);
        verify(problem).getId();
        verify(problemTagRepository).existsByProblemIdAndTagId(resolvedProblemId, tagId);
    }

    @Test
    @DisplayName("Problem이 존재하면 연결된 ProblemTag 목록을 반환한다")
    void getByProblemId_returnsProblemTags() {
        // given
        List<ProblemTag> problemTags = List.of(firstProblemTag, secondProblemTag);

        when(problemFinderService.getById(problemId)).thenReturn(problem);
        when(problem.getId()).thenReturn(resolvedProblemId);
        when(problemTagRepository.findAllByProblemId(resolvedProblemId)).thenReturn(problemTags);

        // when
        List<ProblemTag> result = problemTagFinderService.getByProblemId(problemId);

        // then
        assertThat(result).containsExactly(firstProblemTag, secondProblemTag);

        verify(problemFinderService).getById(problemId);
        verify(problem).getId();
        verify(problemTagRepository).findAllByProblemId(resolvedProblemId);
    }

    @Test
    @DisplayName("Problem이 존재하면 연결된 Tag 목록을 반환한다")
    void getTagsByProblemId_returnsTags() {
        // given
        List<Tag> tags = List.of(firstTag, secondTag);

        when(problemFinderService.getById(problemId)).thenReturn(problem);
        when(problem.getId()).thenReturn(resolvedProblemId);
        when(problemTagRepository.findAllTagsByProblemId(resolvedProblemId)).thenReturn(tags);

        // when
        List<Tag> result = problemTagFinderService.getTagsByProblemId(problemId);

        // then
        assertThat(result).containsExactly(firstTag, secondTag);

        verify(problemFinderService).getById(problemId);
        verify(problem).getId();
        verify(problemTagRepository).findAllTagsByProblemId(resolvedProblemId);
    }

    @Test
    @DisplayName("Problem이 존재하지 않으면 ProblemTag Repository를 조회하지 않고 예외를 전파한다")
    void getByProblemId_problemNotFound_throwsException() {
        // given
        when(problemFinderService.getById(problemId))
                .thenThrow(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> problemTagFinderService.getByProblemId(problemId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);

        verify(problemFinderService).getById(problemId);
        verify(problem, never()).getId();
        verify(problemTagRepository, never()).findAllByProblemId(problemId);
    }
}