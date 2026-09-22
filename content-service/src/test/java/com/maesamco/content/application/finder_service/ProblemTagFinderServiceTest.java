package com.maesamco.content.application.finder_service;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

    private ProblemTagFinderService problemTagFinderService;

    @BeforeEach
    void setUp() {
        problemTagFinderService = new ProblemTagFinderService(
                problemFinderService,
                problemTagRepository
        );
    }

    @Test
    @DisplayName("문제와 태그의 연결이 존재하면 true를 반환한다")
    void existsByProblemIdAndTagId_exists_returnsTrue() {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        when(problemFinderService.getById(problemId))
                .thenReturn(problem);

        when(problem.getId())
                .thenReturn(problemId);

        when(problemTagRepository.existsByProblemIdAndTagId(problemId, tagId))
                .thenReturn(true);

        // when
        boolean result = problemTagFinderService.existsByProblemIdAndTagId(
                problemId,
                tagId
        );

        // then
        assertThat(result).isTrue();

        verify(problemFinderService)
                .getById(problemId);

        verify(problemTagRepository)
                .existsByProblemIdAndTagId(problemId, tagId);
    }

    @Test
    @DisplayName("특정 문제의 문제-태그 연결 목록을 조회한다")
    void getByProblemId_returnsProblemTags() {
        // given
        UUID problemId = UUID.randomUUID();

        ProblemTag first = mock(ProblemTag.class);
        ProblemTag second = mock(ProblemTag.class);

        List<ProblemTag> problemTags = List.of(
                first,
                second
        );

        when(problemFinderService.getById(problemId))
                .thenReturn(problem);

        when(problem.getId())
                .thenReturn(problemId);

        when(problemTagRepository.findAllByProblemId(problemId))
                .thenReturn(problemTags);

        // when
        List<ProblemTag> result = problemTagFinderService.getByProblemId(
                problemId
        );

        // then
        assertThat(result)
                .containsExactly(first, second);

        verify(problemFinderService)
                .getById(problemId);

        verify(problemTagRepository)
                .findAllByProblemId(problemId);
    }

    @Test
    @DisplayName("특정 문제에 연결된 태그 목록을 조회한다")
    void getTagsByProblemId_returnsTags() {
        // given
        UUID problemId = UUID.randomUUID();

        Tag first = mock(Tag.class);
        Tag second = mock(Tag.class);

        List<Tag> tags = List.of(
                first,
                second
        );

        when(problemFinderService.getById(problemId))
                .thenReturn(problem);

        when(problem.getId())
                .thenReturn(problemId);

        when(problemTagRepository.findAllTagsByProblemId(problemId))
                .thenReturn(tags);

        // when
        List<Tag> result = problemTagFinderService.getTagsByProblemId(
                problemId
        );

        // then
        assertThat(result)
                .containsExactly(first, second);

        verify(problemFinderService)
                .getById(problemId);

        verify(problemTagRepository)
                .findAllTagsByProblemId(problemId);
    }
}