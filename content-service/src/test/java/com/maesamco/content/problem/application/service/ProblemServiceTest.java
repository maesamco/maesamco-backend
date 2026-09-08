package com.maesamco.content.problem.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.enums.*;
import com.maesamco.content.problem.domain.repository.ProblemRepository;
import com.maesamco.content.problem.presentation.dto.request.ProblemCreateRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemSearchRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemUpdateRequest;
import com.maesamco.content.problem.presentation.dto.response.ProblemCreateResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemSearchItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ProblemFinder problemFinder;

    private ProblemService problemService;

    private final UUID problemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        problemService = new ProblemService(
                problemRepository,
                problemFinder
        );
    }

    @Test
    @DisplayName("문제를 생성하면 REVIEW_PENDING 상태로 저장하고 생성 응답을 반환한다")
    void createProblem_savesProblemAsReviewPending() {
        // given
        ProblemCreateRequest request = createProblemRequest();

        when(problemRepository.save(any(Problem.class)))
                .thenAnswer(invocation -> {
                    Problem problem = invocation.getArgument(0);
                    ReflectionTestUtils.setField(problem, "id", problemId);
                    return problem;
                });

        // when
        ProblemCreateResponse response =
                problemService.createProblem(request);

        // then
        ArgumentCaptor<Problem> captor =
                ArgumentCaptor.forClass(Problem.class);

        verify(problemRepository).save(captor.capture());

        Problem savedProblem = captor.getValue();

        assertThat(savedProblem.getTitle()).isEqualTo("두 수의 합");
        assertThat(savedProblem.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
        assertThat(savedProblem.getDifficulty()).isEqualTo(ProblemDifficulty.EASY);
        assertThat(savedProblem.getType()).isEqualTo(ProblemType.CODE);
        assertThat(savedProblem.getDescription())
                .isEqualTo("두 정수를 입력받아 합을 출력하세요.");
        assertThat(savedProblem.getStarterCode())
                .isEqualTo("public class Main {}");
        assertThat(savedProblem.getRunningTimeLimit())
                .isEqualTo(RunningTimeLimit.values()[0]);
        assertThat(savedProblem.getRunningMemoryLimit())
                .isEqualTo(RunningMemoryLimit.values()[0]);
        assertThat(savedProblem.getTimerPolicy())
                .isEqualTo(TimerPolicy.NOT_APPLY_TIMEPOLICY);
        assertThat(savedProblem.getSource())
                .isEqualTo(ProblemSource.HUMAN_AUTHORED);

        // create()에서는 DRAFT로 만들어지지만
        // Service에서 REVIEW_PENDING으로 변경한 뒤 저장해야 한다.
        assertThat(savedProblem.getProblemStatus())
                .isEqualTo(ProblemStatus.REVIEW_PENDING);

        assertThat(savedProblem.getCurrentVersionNo()).isEqualTo(1);

        assertThat(response.getId()).isEqualTo(problemId);
        assertThat(response.getTitle()).isEqualTo("두 수의 합");
    }

    @Test
    @DisplayName("문제를 조회하면 ProblemResponse로 변환하여 반환한다")
    void getProblem_returnsProblemResponse() {
        // given
        Problem problem = createProblem();
        ReflectionTestUtils.setField(problem, "id", problemId);

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        // when
        ProblemResponse response =
                problemService.getProblem(problemId);

        // then
        assertThat(response.getId()).isEqualTo(problemId);
        assertThat(response.getTitle()).isEqualTo("두 수의 합");
        assertThat(response.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
        assertThat(response.getDifficulty()).isEqualTo(ProblemDifficulty.EASY);
        assertThat(response.getType()).isEqualTo(ProblemType.CODE);
        assertThat(response.getStarterCode())
                .isEqualTo("public class Main {}");
        assertThat(response.getCurrentVersionNo()).isEqualTo(1);

        verify(problemFinder).getProblem(problemId);
    }

    @Test
    @DisplayName("문제 검색 결과를 PageResponse로 변환하여 반환한다")
    void searchProblems_returnsPageResponse() {
        // given
        ProblemSearchRequest request = new ProblemSearchRequest();
        Pageable pageable = PageRequest.of(0, 20);

        Problem problem = createProblem();
        ReflectionTestUtils.setField(problem, "id", problemId);

        Page<Problem> page =
                new PageImpl<>(List.of(problem), pageable, 1);

        when(problemRepository.searchProblems(request, pageable))
                .thenReturn(page);

        // when
        PageResponse<ProblemSearchItemResponse> response =
                problemService.searchProblems(request, pageable);

        // then
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).getId()).isEqualTo(problemId);
        assertThat(response.content().get(0).getTitle()).isEqualTo("두 수의 합");
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();

        verify(problemRepository)
                .searchProblems(request, pageable);
    }

    @Test
    @DisplayName("수정 요청에 포함된 필드만 변경하고 버전은 한 번만 증가한다")
    void updateProblem_updatesProvidedFieldsAndIncreasesVersionOnce() {
        // given
        Problem problem = createProblem();

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        ProblemUpdateRequest request = new ProblemUpdateRequest();

        ReflectionTestUtils.setField(
                request,
                "title",
                "변경된 문제 제목"
        );

        ReflectionTestUtils.setField(
                request,
                "difficulty",
                ProblemDifficulty.HARD
        );

        // when
        ProblemResponse response =
                problemService.updateProblem(problemId, request);

        // then
        assertThat(response.getTitle())
                .isEqualTo("변경된 문제 제목");

        assertThat(response.getDifficulty())
                .isEqualTo(ProblemDifficulty.HARD);

        // 요청하지 않은 값은 그대로
        assertThat(response.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(response.getDescription())
                .isEqualTo("두 정수를 입력받아 합을 출력하세요.");

        // 두 필드를 수정했지만 한 요청이므로 버전은 한 번만 증가
        assertThat(response.getCurrentVersionNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("starterCode에 값을 전달하면 새로운 값으로 변경하고 버전을 증가시킨다")
    void updateProblem_updatesStarterCodeWhenValuePresent() {
        // given
        Problem problem = createProblem();

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        ProblemUpdateRequest request = new ProblemUpdateRequest();

        ReflectionTestUtils.setField(
                request,
                "starterCode",
                JsonNullable.of("class Solution {}")
        );

        // when
        ProblemResponse response =
                problemService.updateProblem(problemId, request);

        // then
        assertThat(response.getStarterCode())
                .isEqualTo("class Solution {}");

        assertThat(response.getCurrentVersionNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("starterCode에 null을 명시하면 기존 값을 null로 변경하고 버전을 증가시킨다")
    void updateProblem_clearsStarterCodeWhenExplicitNull() {
        // given
        Problem problem = createProblem();

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        ProblemUpdateRequest request = new ProblemUpdateRequest();

        ReflectionTestUtils.setField(
                request,
                "starterCode",
                JsonNullable.of(null)
        );

        // when
        ProblemResponse response =
                problemService.updateProblem(problemId, request);

        // then
        assertThat(response.getStarterCode()).isNull();
        assertThat(response.getCurrentVersionNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("starterCode를 생략하면 기존 값을 유지하고 버전을 증가시키지 않는다")
    void updateProblem_keepsStarterCodeWhenUndefined() {
        // given
        Problem problem = createProblem();

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        // 기본값이 JsonNullable.undefined()
        ProblemUpdateRequest request = new ProblemUpdateRequest();

        // when
        ProblemResponse response =
                problemService.updateProblem(problemId, request);

        // then
        assertThat(response.getStarterCode())
                .isEqualTo("public class Main {}");

        assertThat(response.getCurrentVersionNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("starterCode JsonNullable 객체 자체가 null이면 STARTER_CODE_NOT_INITIALIZED 예외가 발생한다")
    void updateProblem_throwsWhenStarterCodeIsNotInitialized() {
        // given
        Problem problem = createProblem();

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        ProblemUpdateRequest request = new ProblemUpdateRequest();

        ReflectionTestUtils.setField(
                request,
                "starterCode",
                null
        );

        // when & then
        assertThatThrownBy(
                () -> problemService.updateProblem(problemId, request)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode()
                )
                .isEqualTo(ErrorCode.STARTER_CODE_NOT_INITIALIZED);

        assertThat(problem.getCurrentVersionNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("문제를 삭제하면 요청한 사용자 ID로 soft delete 처리한다")
    void deleteProblem_softDeletesProblem() {
        // given
        UUID userId = UUID.randomUUID();
        Problem problem = createProblem();

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        // when
        problemService.deleteProblem(problemId, userId);

        // then
        assertThat(problem.isDeleted()).isTrue();
        assertThat(problem.getDeletedAt()).isNotNull();
        assertThat(problem.getDeletedBy()).isEqualTo(userId);

        verify(problemFinder).getProblem(problemId);
    }

    private ProblemCreateRequest createProblemRequest() {
        ProblemCreateRequest request = new ProblemCreateRequest();

        ReflectionTestUtils.setField(
                request, "title", "두 수의 합"
        );
        ReflectionTestUtils.setField(
                request, "language", ProgrammingLanguage.JAVA
        );
        ReflectionTestUtils.setField(
                request, "difficulty", ProblemDifficulty.EASY
        );
        ReflectionTestUtils.setField(
                request, "type", ProblemType.CODE
        );
        ReflectionTestUtils.setField(
                request,
                "description",
                "두 정수를 입력받아 합을 출력하세요."
        );
        ReflectionTestUtils.setField(
                request,
                "starterCode",
                "public class Main {}"
        );
        ReflectionTestUtils.setField(
                request,
                "runningTimeLimit",
                RunningTimeLimit.values()[0]
        );
        ReflectionTestUtils.setField(
                request,
                "runningMemoryLimit",
                RunningMemoryLimit.values()[0]
        );
        ReflectionTestUtils.setField(
                request,
                "timerPolicy",
                TimerPolicy.NOT_APPLY_TIMEPOLICY
        );
        ReflectionTestUtils.setField(
                request,
                "source",
                ProblemSource.HUMAN_AUTHORED
        );

        return request;
    }

    private Problem createProblem() {
        return Problem.create(
                "두 수의 합",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수를 입력받아 합을 출력하세요.",
                "public class Main {}",
                RunningTimeLimit.values()[0],
                RunningMemoryLimit.values()[0],
                TimerPolicy.NOT_APPLY_TIMEPOLICY,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.DRAFT
        );
    }
}