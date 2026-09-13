package com.maesamco.content.quicktest;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.application.port.ProblemTagFinder;
import com.maesamco.content.problem.application.service.ProblemTagService;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.ProblemTag;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.repository.ProblemTagRepository;
import com.maesamco.content.tag.application.port.TagFinder;
import com.maesamco.content.tag.domain.entity.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemTagServiceTest {

    @Mock
    private ProblemTagRepository problemTagRepository;

    @Mock
    private ProblemFinder problemFinder;

    @Mock
    private TagFinder tagFinder;

    @Mock
    private ProblemTagFinder problemTagFinder;

    @InjectMocks
    private ProblemTagService problemTagService;


    @Test
    @DisplayName("문제에 태그를 연결한다.")
    void addProblemTag_success() {

        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        Problem problem = mock(Problem.class);
        Tag tag = mock(Tag.class);

        when(problemFinder.getProblem(problemId)).thenReturn(problem);
        when(tagFinder.getTag(tagId)).thenReturn(tag);

        // when
        problemTagService.addTagToProblem(problemId, tagId);

        // then
        ArgumentCaptor<ProblemTag> captor = ArgumentCaptor.forClass(ProblemTag.class);
        verify(problemTagRepository).save(captor.capture());

        ProblemTag savedProblemTag = captor.getValue();

        assertThat(savedProblemTag.getProblemId()).isEqualTo(problemId);
        assertThat(savedProblemTag.getTagId()).isEqualTo(tagId);

        System.out.println("===== 문제 태그 연결 결과 =====");
        System.out.println("problemId = " + savedProblemTag.getProblemId());
        System.out.println("tagId = " + savedProblemTag.getTagId());
    }


    @Test
    @DisplayName("이미 연결된 태그는 다시 연결하지 않는다.")
    void addTagToProblem_duplicate() {

        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        Problem problem = mock(Problem.class);
        Tag tag = mock(Tag.class);

        when(problemFinder.getProblem(problemId)).thenReturn(problem);
        when(tagFinder.getTag(tagId)).thenReturn(tag);
        when(problemTagRepository.existsByProblemIdAndTagId(problemId, tagId)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> problemTagService.addTagToProblem(problemId, tagId))
                .isInstanceOf(BusinessException.class);

        verify(problemTagRepository, never()).save(any(ProblemTag.class));

        System.out.println("===== 문제 태그 중복 연결 결과 =====");
        System.out.println("problemId = " + problemId);
        System.out.println("tagId = " + tagId);
        System.out.println("이미 연결된 태그이므로 저장되지 않음");
    }

    @Test
    @DisplayName("존재하지 않는 문제에는 태그를 연결하지 않는다.")
    void addTagToProblem_problemNotFound() {

        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        BusinessException exception = mock(BusinessException.class);

        when(problemFinder.getProblem(problemId)).thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> problemTagService.addTagToProblem(problemId, tagId))
                .isSameAs(exception);

        verify(tagFinder, never()).getTag(any());
        verify(problemTagRepository, never()).save(any(ProblemTag.class));

        System.out.println("===== 존재하지 않는 문제 태그 연결 실패 =====");
        System.out.println("problemId = " + problemId);
        System.out.println("tagId = " + tagId);
        System.out.println("Problem 조회 단계에서 실패하여 태그가 저장되지 않음");
    }

    @Test
    @DisplayName("발행된 문제의 태그 목록을 조회한다.")
    void searchProblemTags_publishedProblem_success() {

        // given
        UUID problemId = UUID.randomUUID();

        Problem problem = mock(Problem.class);

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        when(problem.getProblemStatus())
                .thenReturn(ProblemStatus.PUBLISHED);

        var pageable =
                org.springframework.data.domain.PageRequest.of(0, 10);

        when(problemTagRepository.searchTagsByProblemId(
                problemId,
                pageable
        )).thenReturn(
                org.springframework.data.domain.Page.empty(pageable)
        );

        // when
        var response =
                problemTagService.searchProblemTags(
                        problemId,
                        pageable
                );

        // then
        assertThat(response.content()).isEmpty();

        verify(problemFinder)
                .getProblem(problemId);

        verify(problemTagRepository)
                .searchTagsByProblemId(
                        problemId,
                        pageable
                );
    }

    @Test
    @DisplayName("발행되지 않은 문제의 태그 목록은 조회할 수 없다.")
    void searchProblemTags_reviewPendingProblem_denied() {

        // given
        UUID problemId = UUID.randomUUID();

        Problem problem = mock(Problem.class);

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        when(problem.getProblemStatus())
                .thenReturn(ProblemStatus.REVIEW_PENDING);

        var pageable =
                org.springframework.data.domain.PageRequest.of(0, 10);

        // when & then
        assertThatThrownBy(
                () -> problemTagService.searchProblemTags(
                        problemId,
                        pageable
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(ErrorCode.PROBLEM_NOT_FOUND)
                );

        verify(problemFinder)
                .getProblem(problemId);

        verify(problemTagRepository, never())
                .searchTagsByProblemId(
                        any(UUID.class),
                        any()
                );
    }
}
