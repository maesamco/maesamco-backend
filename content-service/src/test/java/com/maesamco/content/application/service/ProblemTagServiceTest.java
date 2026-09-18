package com.maesamco.content.application.service;

import com.maesamco.content.application.input_port.ProblemFinder;
import com.maesamco.content.application.input_port.TagFinder;
import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.presentation.response.TagResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemTagServiceTest {

    @Mock
    private ProblemTagRepository problemTagRepository;

    @Mock
    private ProblemFinder problemFinder;

    @Mock
    private TagFinder tagFinder;

    @InjectMocks
    private ProblemTagService problemTagService;

    @Test
    @DisplayName("발행된 문제의 태그 목록을 조회한다")
    void searchProblemTags_success() {
        // given
        UUID problemId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        Problem problem = mock(Problem.class);

        when(problemFinder.getById(problemId))
                .thenReturn(problem);

        when(problem.getProblemStatus())
                .thenReturn(ProblemStatus.PUBLISHED);

        when(problemTagRepository.searchTagsByProblemId(
                problemId,
                pageable
        )).thenReturn(Page.empty(pageable));

        // when
        PageResponse<TagResponse> result =
                problemTagService.searchProblemTags(
                        problemId,
                        pageable
                );

        // then
        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);

        verify(problemFinder).getById(problemId);
        verify(problemTagRepository)
                .searchTagsByProblemId(problemId, pageable);
    }

    @Test
    @DisplayName("발행되지 않은 문제의 태그를 조회하면 PROBLEM_NOT_FOUND 예외가 발생한다")
    void searchProblemTags_notPublished_throwsException() {
        // given
        UUID problemId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        Problem problem = mock(Problem.class);

        when(problemFinder.getById(problemId))
                .thenReturn(problem);

        when(problem.getProblemStatus())
                .thenReturn(ProblemStatus.REVIEW_PENDING);

        // when & then
        assertThatThrownBy(
                () -> problemTagService.searchProblemTags(
                        problemId,
                        pageable
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode()
                )
                .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);

        verifyNoInteractions(problemTagRepository);
    }

    @Test
    @DisplayName("문제에 태그를 등록한다")
    void addTagToProblem_success() {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        when(problemFinder.getById(problemId))
                .thenReturn(mock(Problem.class));

        when(tagFinder.getById(tagId))
                .thenReturn(mock(Tag.class));

        when(problemTagRepository.existsByProblemIdAndTagId(
                problemId,
                tagId
        )).thenReturn(false);

        // when
        problemTagService.addTagToProblem(
                problemId,
                tagId
        );

        // then
        ArgumentCaptor<ProblemTag> captor =
                ArgumentCaptor.forClass(ProblemTag.class);

        verify(problemTagRepository)
                .save(captor.capture());

        ProblemTag saved = captor.getValue();

        assertThat(saved.getProblemId())
                .isEqualTo(problemId);
        assertThat(saved.getTagId())
                .isEqualTo(tagId);
    }

    @Test
    @DisplayName("이미 등록된 태그를 다시 등록하면 예외가 발생한다")
    void addTagToProblem_duplicate_throwsException() {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        when(problemFinder.getById(problemId))
                .thenReturn(mock(Problem.class));

        when(tagFinder.getById(tagId))
                .thenReturn(mock(Tag.class));

        when(problemTagRepository.existsByProblemIdAndTagId(
                problemId,
                tagId
        )).thenReturn(true);

        // when & then
        assertThatThrownBy(
                () -> problemTagService.addTagToProblem(
                        problemId,
                        tagId
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.PROBLEM_TAG_ALREADY_EXISTS
                );

        verify(problemTagRepository, never())
                .save(any());
    }

    @Test
    @DisplayName("문제에 등록된 태그를 제거한다")
    void removeTagFromProblem_success() {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        ProblemTag problemTag =
                ProblemTag.create(problemId, tagId);

        when(problemFinder.getById(problemId))
                .thenReturn(mock(Problem.class));

        when(problemTagRepository.findByProblemIdAndTagId(
                problemId,
                tagId
        )).thenReturn(Optional.of(problemTag));

        // when
        problemTagService.removeTagFromProblem(
                problemId,
                tagId
        );

        // then
        verify(problemTagRepository)
                .delete(problemTag);
    }

    @Test
    @DisplayName("문제에 등록되지 않은 태그를 제거하면 예외가 발생한다")
    void removeTagFromProblem_notFound_throwsException() {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        when(problemFinder.getById(problemId))
                .thenReturn(mock(Problem.class));

        when(problemTagRepository.findByProblemIdAndTagId(
                problemId,
                tagId
        )).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> problemTagService.removeTagFromProblem(
                        problemId,
                        tagId
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.PROBLEM_TAG_NOT_FOUND
                );

        verify(problemTagRepository, never())
                .delete(any());
    }
}