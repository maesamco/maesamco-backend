package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.command.ProblemCreateCommand;
import com.maesamco.content.application.command.ProblemUpdateCommand;
import com.maesamco.content.application.command.UpdateField;
import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.application.query.ProblemSearchQuery;
import com.maesamco.content.application.result.ProblemResult;
import com.maesamco.content.application.result.ProblemSearchResult;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.*;
import com.maesamco.content.domain.repository.problem.ProblemCommandRepository;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.domain.repository.problem.ProblemSearchCondition;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemCommandRepository problemCommandRepository;

    @Mock
    private ProblemQueryRepository problemQueryRepository;

    @Mock
    private ProblemVersionRepository problemVersionRepository;

    @Mock
    private ProblemFinder problemFinder;

    @Mock
    private ProblemPublicationFacade problemPublicationFacade;

    @InjectMocks
    private ProblemService problemService;

    private final UUID problemId =
            UUID.randomUUID();

    @Test
    @DisplayName("문제를 생성하면 REVIEW_PENDING 상태로 저장하고 초기 버전을 생성한다")
    void createProblem_success() {
        // given
        ProblemCreateCommand command =
                createCommand();

        when(problemCommandRepository.save(
                any(Problem.class)
        )).thenAnswer(invocation -> {
            Problem problem =
                    invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    problem,
                    "id",
                    problemId
            );

            return problem;
        });

        // when
        ProblemResult result =
                problemService.createProblem(command);

        // then
        assertThat(result)
                .isNotNull();

        ArgumentCaptor<Problem> problemCaptor =
                ArgumentCaptor.forClass(
                        Problem.class
                );

        verify(problemCommandRepository)
                .save(problemCaptor.capture());

        Problem savedProblem =
                problemCaptor.getValue();

        assertThat(savedProblem.getTitle())
                .isEqualTo("두 수의 합");

        assertThat(savedProblem.getProblemStatus())
                .isEqualTo(
                        ProblemStatus.REVIEW_PENDING
                );

        assertThat(savedProblem.getCurrentVersionNo())
                .isEqualTo(1);

        ArgumentCaptor<ProblemVersion> versionCaptor =
                ArgumentCaptor.forClass(
                        ProblemVersion.class
                );

        verify(problemVersionRepository)
                .save(versionCaptor.capture());

        ProblemVersion version =
                versionCaptor.getValue();

        assertThat(version.getProblemId())
                .isEqualTo(problemId);

        assertThat(version.getVersionNo())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 문제 검색은 요청한 상태 조건을 강제로 PUBLISHED로 변경하지 않는다")
    void searchProblemsForAdmin_preservesRequestedStatus() {

        ProblemSearchQuery query =
                new ProblemSearchQuery(
                        null,
                        null,
                        null,
                        ProblemStatus.REVIEW_PENDING,
                        null,
                        null
                );

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        ProblemSearchCondition condition =
                query.toCondition();

        when(
                problemQueryRepository.searchProblems(
                        any(ProblemSearchCondition.class),
                        eq(pageable)
                )
        ).thenReturn(
                Page.empty()
        );

        problemService.searchProblemsForAdmin(
                query,
                pageable
        );

        assertThat(
                query.getProblemStatus()
        ).isEqualTo(
                ProblemStatus.REVIEW_PENDING
        );

        ArgumentCaptor<ProblemSearchCondition>
                conditionCaptor =
                ArgumentCaptor.forClass(
                        ProblemSearchCondition.class
                );

        verify(problemQueryRepository)
                .searchProblems(
                        conditionCaptor.capture(),
                        eq(pageable)
                );

        assertThat(
                conditionCaptor.getValue()
                        .getProblemStatus()
        ).isEqualTo(
                ProblemStatus.REVIEW_PENDING
        );
    }

    @Test
    @DisplayName("관리자 문제 조회는 ProblemFinder를 통해 문제를 조회한다")
    void getProblemForAdmin_success() {
        // given
        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        when(problemFinder.getById(problemId))
                .thenReturn(problem);

        // when
        ProblemResult result =
                problemService.getProblemForAdmin(
                        problemId
                );

        // then
        assertThat(result)
                .isNotNull();

        verify(problemFinder)
                .getById(problemId);

        verify(problemFinder, never())
                .lockById(any());
    }

    @Test
    @DisplayName("사용자는 PUBLISHED 문제를 조회할 수 있다")
    void getProblemForUser_published_success() {
        // given
        Problem problem =
                createProblem(
                        ProblemStatus.PUBLISHED
                );

        when(problemFinder.getById(problemId))
                .thenReturn(problem);

        // when
        ProblemResult result =
                problemService.getProblemForUser(
                        problemId
                );

        // then
        assertThat(result)
                .isNotNull();

        verify(problemFinder)
                .getById(problemId);

        verify(problemFinder, never())
                .lockById(any());
    }

    @Test
    @DisplayName("PUBLISHED 상태가 아닌 문제를 사용자가 조회하면 예외가 발생한다")
    void getProblemForUser_notPublished_throwsException() {
        // given
        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        when(problemFinder.getById(problemId))
                .thenReturn(problem);

        // when & then
        assertThatThrownBy(
                () -> problemService.getProblemForUser(
                        problemId
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(exception ->
                        ((BusinessException) exception)
                                .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.PROBLEM_NOT_FOUND
                );

        verify(problemFinder)
                .getById(problemId);

        verify(problemFinder, never())
                .lockById(any());
    }

    @Test
    @DisplayName("문제 검색은 Query를 Condition으로 변환하여 조회한다")
    void searchProblems_success() {
        // given
        ProblemSearchQuery query =
                mock(ProblemSearchQuery.class);

        ProblemSearchCondition condition =
                mock(ProblemSearchCondition.class);

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        Problem problem =
                createProblem(
                        ProblemStatus.PUBLISHED
                );

        when(query.toCondition())
                .thenReturn(condition);

        when(problemQueryRepository.searchProblems(
                condition,
                pageable
        )).thenReturn(
                new PageImpl<>(
                        List.of(problem),
                        pageable,
                        1
                )
        );

        // when
        Page<ProblemSearchResult> result =
                problemService.searchProblems(
                        query,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .hasSize(1);

        verify(problemQueryRepository)
                .searchProblems(
                        condition,
                        pageable
                );

        verifyNoInteractions(
                problemCommandRepository
        );
    }

    @Test
    @DisplayName("수정 요청에 포함된 필드만 변경하고 버전을 한 번 증가시킨다")
    void updateProblem_success() {
        // given
        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        ProblemUpdateCommand command =
                mock(ProblemUpdateCommand.class);

        // 수정 대상 Problem은 Command Repository를 사용하는
        // lockById를 통해 조회한다.
        when(problemFinder.lockById(problemId))
                .thenReturn(problem);

        when(command.getLockVersion())
                .thenReturn(0L);

        when(command.getTitle())
                .thenReturn("변경된 문제");

        when(command.getDifficulty())
                .thenReturn(
                        ProblemDifficulty.HARD
                );

        // starterCode가 요청에 포함되지 않았음을 표현한다.
        when(command.getStarterCode())
                .thenReturn(
                        UpdateField.undefined()
                );

        when(command.getLessonId())
                .thenReturn(
                        UpdateField.undefined()
                );

        // when
        ProblemResult result =
                problemService.updateProblem(
                        problemId,
                        command
                );

        // then
        assertThat(result)
                .isNotNull();

        assertThat(problem.getTitle())
                .isEqualTo("변경된 문제");

        assertThat(problem.getDifficulty())
                .isEqualTo(
                        ProblemDifficulty.HARD
                );

        assertThat(problem.getStarterCode())
                .isEqualTo(
                        "public class Main {}"
                );

        assertThat(problem.getCurrentVersionNo())
                .isEqualTo(2);

        verify(problemFinder)
                .lockById(problemId);

        verify(problemFinder, never())
                .getById(problemId);

        verify(problemCommandRepository)
                .flush();

        ArgumentCaptor<ProblemVersion> captor =
                ArgumentCaptor.forClass(
                        ProblemVersion.class
                );

        verify(problemVersionRepository)
                .save(captor.capture());

        assertThat(
                captor.getValue()
                        .getVersionNo()
        ).isEqualTo(2);
    }

    @Test
    @DisplayName("이슈 #254 — PUBLISHED 문제의 채점 관련 필드(language)가 바뀌면 일반 스냅샷 대신 재발행한다")
    void updateProblem_publishedAndLanguageChanged_republishesInsteadOfSnapshot() {
        // given
        Problem problem = createProblem(ProblemStatus.PUBLISHED);

        ProblemUpdateCommand command = mock(ProblemUpdateCommand.class);

        when(problemFinder.lockById(problemId)).thenReturn(problem);
        when(command.getLockVersion()).thenReturn(0L);
        when(command.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(command.getStarterCode()).thenReturn(UpdateField.undefined());

        when(command.getLessonId())
                .thenReturn(
                        UpdateField.undefined()
                );

        // when
        problemService.updateProblem(problemId, command);

        // then
        assertThat(problem.getCurrentVersionNo()).isEqualTo(2);

        verify(problemPublicationFacade).republishExistingVersion(problem);
        verifyNoInteractions(problemVersionRepository);
        verify(problemCommandRepository).flush();
    }

    @Test
    @DisplayName("이슈 #254 — PUBLISHED 문제라도 채점과 무관한 필드(title)만 바뀌면 일반 스냅샷을 저장한다")
    void updateProblem_publishedButOnlyNonGradingFieldChanged_savesPlainSnapshot() {
        // given
        Problem problem = createProblem(ProblemStatus.PUBLISHED);

        ProblemUpdateCommand command = mock(ProblemUpdateCommand.class);

        when(problemFinder.lockById(problemId)).thenReturn(problem);
        when(command.getLockVersion()).thenReturn(0L);
        when(command.getTitle()).thenReturn("제목만 변경");
        when(command.getStarterCode()).thenReturn(UpdateField.undefined());

        when(command.getLessonId())
                .thenReturn(
                        UpdateField.undefined()
                );

        // when
        problemService.updateProblem(problemId, command);

        // then
        assertThat(problem.getTitle()).isEqualTo("제목만 변경");
        assertThat(problem.getCurrentVersionNo()).isEqualTo(2);

        verify(problemVersionRepository).save(any(ProblemVersion.class));
        verifyNoInteractions(problemPublicationFacade);
        verify(problemCommandRepository).flush();
    }

    @Test
    @DisplayName("이슈 #254 — PUBLISHED가 아닌 문제는 채점 관련 필드가 바뀌어도 재발행하지 않는다")
    void updateProblem_notPublishedAndLanguageChanged_savesPlainSnapshot() {
        // given
        Problem problem = createProblem(ProblemStatus.REVIEW_PENDING);

        ProblemUpdateCommand command = mock(ProblemUpdateCommand.class);

        when(problemFinder.lockById(problemId)).thenReturn(problem);
        when(command.getLockVersion()).thenReturn(0L);
        when(command.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(command.getStarterCode()).thenReturn(UpdateField.undefined());

        when(command.getLessonId())
                .thenReturn(
                        UpdateField.undefined()
                );

        // when
        problemService.updateProblem(problemId, command);

        // then
        verify(problemVersionRepository).save(any(ProblemVersion.class));
        verifyNoInteractions(problemPublicationFacade);
        verify(problemCommandRepository).flush();
    }

    @Test
    @DisplayName("starterCode가 요청에 포함되지 않으면 기존 값을 유지한다")
    void updateProblem_starterCodeUndefined_keepsExistingValue() {
        // given
        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        ProblemUpdateCommand command =
                mock(ProblemUpdateCommand.class);

        when(problemFinder.lockById(problemId))
                .thenReturn(problem);

        when(command.getLockVersion())
                .thenReturn(0L);

        when(command.getTitle())
                .thenReturn("변경된 문제");

        // 요청에 starterCode 필드 자체가 없는 경우
        when(command.getStarterCode())
                .thenReturn(
                        UpdateField.undefined()
                );

        when(command.getLessonId())
                .thenReturn(
                        UpdateField.undefined()
                );

        // when
        problemService.updateProblem(
                problemId,
                command
        );

        // then
        assertThat(problem.getStarterCode())
                .isEqualTo(
                        "public class Main {}"
                );

        verify(problemFinder)
                .lockById(problemId);

        verify(problemCommandRepository)
                .flush();
    }

    @Test
    @DisplayName("starterCode가 명시적으로 null이면 기존 값을 null로 변경한다")
    void updateProblem_starterCodeNull_changesToNull() {
        // given
        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        ProblemUpdateCommand command =
                mock(ProblemUpdateCommand.class);

        when(problemFinder.lockById(problemId))
                .thenReturn(problem);

        when(command.getLockVersion())
                .thenReturn(0L);

        // 요청에 starterCode가 존재하지만 값이 null인 경우
        when(command.getStarterCode())
                .thenReturn(
                        UpdateField.of(null)
                );

        when(command.getLessonId())
                .thenReturn(
                        UpdateField.undefined()
                );

        // when
        problemService.updateProblem(
                problemId,
                command
        );

        // then
        assertThat(problem.getStarterCode())
                .isNull();

        assertThat(problem.getCurrentVersionNo())
                .isEqualTo(2);

        verify(problemFinder)
                .lockById(problemId);

        verify(problemCommandRepository)
                .flush();

        verify(problemVersionRepository)
                .save(any(ProblemVersion.class));
    }

    @Test
    @DisplayName("starterCode에 실제 값이 전달되면 해당 값으로 변경한다")
    void updateProblem_starterCodeValue_changesValue() {
        // given
        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        ProblemUpdateCommand command =
                mock(ProblemUpdateCommand.class);

        when(problemFinder.lockById(problemId))
                .thenReturn(problem);

        when(command.getLockVersion())
                .thenReturn(0L);

        // 요청에 starterCode 실제 값이 포함된 경우
        when(command.getStarterCode())
                .thenReturn(
                        UpdateField.of(
                                "public class UpdatedMain {}"
                        )
                );

        when(command.getLessonId())
                .thenReturn(
                        UpdateField.undefined()
                );

        // when
        problemService.updateProblem(
                problemId,
                command
        );

        // then
        assertThat(problem.getStarterCode())
                .isEqualTo(
                        "public class UpdatedMain {}"
                );

        assertThat(problem.getCurrentVersionNo())
                .isEqualTo(2);

        verify(problemFinder)
                .lockById(problemId);

        verify(problemCommandRepository)
                .flush();

        verify(problemVersionRepository)
                .save(any(ProblemVersion.class));
    }

    @Test
    @DisplayName("요청 lockVersion이 현재 값과 다르면 동시 수정 예외가 발생한다")
    void updateProblem_staleLockVersion_throwsException() {
        // given
        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        ProblemUpdateCommand command =
                mock(ProblemUpdateCommand.class);

        // 수정 대상 Problem은 Query 조회가 아니라
        // Command 경로를 통해 가져온다.
        when(problemFinder.lockById(problemId))
                .thenReturn(problem);

        when(command.getLockVersion())
                .thenReturn(1L);

        // when & then
        assertThatThrownBy(
                () -> problemService.updateProblem(
                        problemId,
                        command
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(exception ->
                        ((BusinessException) exception)
                                .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY
                );

        verify(problemFinder)
                .lockById(problemId);

        verify(problemFinder, never())
                .getById(problemId);

        verifyNoInteractions(
                problemVersionRepository
        );

        verify(
                problemCommandRepository,
                never()
        ).flush();
    }

    @Test
    @DisplayName("flush에서 낙관적 락 충돌이 발생하면 예외를 그대로 전파한다")
    void updateProblem_flushConflict_propagatesException() {
        // given
        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        ProblemUpdateCommand command =
                mock(ProblemUpdateCommand.class);

        when(problemFinder.lockById(problemId))
                .thenReturn(problem);

        when(command.getLockVersion())
                .thenReturn(0L);

        when(command.getTitle())
                .thenReturn("동시 수정");

        when(command.getStarterCode())
                .thenReturn(
                        UpdateField.undefined()
                );

        when(command.getLessonId())
                .thenReturn(
                        UpdateField.undefined()
                );

        doThrow(
                new ObjectOptimisticLockingFailureException(
                        Problem.class,
                        problemId
                )
        )
                .when(problemCommandRepository)
                .flush();

        // when & then
        assertThatThrownBy(
                () -> problemService.updateProblem(
                        problemId,
                        command
                )
        )
                .isInstanceOf(
                        ObjectOptimisticLockingFailureException.class
                );

        verify(problemFinder)
                .lockById(problemId);

        verify(problemFinder, never())
                .getById(problemId);

        verify(problemCommandRepository)
                .flush();
    }

    @Test
    @DisplayName("문제를 삭제하면 수정용 조회 후 soft delete 처리하고 flush한다")
    void deleteProblem_success() {
        // given
        UUID userId =
                UUID.randomUUID();

        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        // 삭제 또한 상태를 변경하는 Command이므로
        // Command Repository 기반의 lockById를 사용한다.
        when(problemFinder.lockById(problemId))
                .thenReturn(problem);

        // when
        problemService.deleteProblem(
                problemId,
                userId
        );

        // then
        assertThat(problem.isDeleted())
                .isTrue();

        assertThat(problem.getDeletedAt())
                .isNotNull();

        assertThat(problem.getDeletedBy())
                .isEqualTo(userId);

        verify(problemFinder)
                .lockById(problemId);

        verify(problemFinder, never())
                .getById(problemId);

        verify(problemCommandRepository)
                .flush();
    }

    private ProblemCreateCommand createCommand() {
        ProblemCreateCommand command =
                mock(ProblemCreateCommand.class);

        when(command.getTitle())
                .thenReturn("두 수의 합");

        when(command.getLanguage())
                .thenReturn(
                        ProgrammingLanguage.JAVA
                );

        when(command.getDifficulty())
                .thenReturn(
                        ProblemDifficulty.EASY
                );

        when(command.getType())
                .thenReturn(
                        ProblemType.CODE
                );

        when(command.getDescription())
                .thenReturn(
                        "두 정수를 더하세요."
                );

        when(command.getStarterCode())
                .thenReturn(
                        "public class Main {}"
                );

        when(command.getRunningTimeLimit())
                .thenReturn(
                        RunningTimeLimit.SECOND_1
                );

        when(command.getRunningMemoryLimit())
                .thenReturn(
                        RunningMemoryLimit.MB_128
                );

        when(command.getTimerPolicy())
                .thenReturn(
                        TimerPolicy.APPLY60
                );

        when(command.getSource())
                .thenReturn(
                        ProblemSource.HUMAN_AUTHORED
                );

        return command;
    }

    private Problem createProblem(
            ProblemStatus status
    ) {
        Problem problem =
                Problem.create(
                        "두 수의 합",
                        ProgrammingLanguage.JAVA,
                        ProblemDifficulty.EASY,
                        ProblemType.CODE,
                        "두 정수를 더하세요.",
                        "public class Main {}",
                        RunningTimeLimit.SECOND_1,
                        RunningMemoryLimit.MB_128,
                        TimerPolicy.APPLY60,
                        ProblemSource.HUMAN_AUTHORED,
                        status
                );

        ReflectionTestUtils.setField(
                problem,
                "id",
                problemId
        );

        ReflectionTestUtils.setField(
                problem,
                "lockVersion",
                0L
        );

        return problem;
    }
}
