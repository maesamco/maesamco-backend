package com.maesamco.content.quicktest;

import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.application.service.ProblemService;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.ProblemVersion;
import com.maesamco.content.problem.domain.enums.*;
import com.maesamco.content.problem.domain.repository.ProblemRepository;
import com.maesamco.content.problem.domain.repository.ProblemVersionRepository;
import com.maesamco.content.problem.presentation.dto.request.ProblemUpdateRequest;
import com.maesamco.content.problem.presentation.dto.response.ProblemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ProblemFinder problemFinder;

    @Mock
    private ProblemVersionRepository problemVersionRepository;

    @InjectMocks
    private ProblemService problemService;

    @Test
    @DisplayName(
            "관리자 문제 단건 조회 시 문제 정보를 반환한다"
    )
    void getProblem_success() {
        // given
        UUID problemId = UUID.randomUUID();

        Problem problem = mock(Problem.class);

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        when(problem.getId())
                .thenReturn(problemId);

        when(problem.getTitle())
                .thenReturn("두 수의 합");

        when(problem.getLanguage())
                .thenReturn(ProgrammingLanguage.JAVA);

        when(problem.getDifficulty())
                .thenReturn(ProblemDifficulty.EASY);

        when(problem.getType())
                .thenReturn(ProblemType.CODE);

        when(problem.getDescription())
                .thenReturn(
                        "두 정수를 입력받아 두 수의 합을 출력하세요."
                );

        when(problem.getStarterCode())
                .thenReturn("public class Main {\n}");

        when(problem.getRunningTimeLimit())
                .thenReturn(RunningTimeLimit.SECOND_1);

        when(problem.getRunningMemoryLimit())
                .thenReturn(RunningMemoryLimit.MB_128);

        when(problem.getTimerPolicy())
                .thenReturn(
                        TimerPolicy.NOT_APPLY_TIMEPOLICY
                );

        when(problem.getSource())
                .thenReturn(ProblemSource.HUMAN_AUTHORED);

        when(problem.getProblemStatus())
                .thenReturn(ProblemStatus.DRAFT);

        when(problem.getCurrentVersionNo())
                .thenReturn(1);

        when(problem.getLockVersion())
                .thenReturn(0L);

        // when
        ProblemResponse response =
                problemService.getProblemForAdmin(problemId);

        // then
        assertThat(response.getId())
                .isEqualTo(problemId);

        assertThat(response.getTitle())
                .isEqualTo("두 수의 합");

        assertThat(response.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(response.getDifficulty())
                .isEqualTo(ProblemDifficulty.EASY);

        assertThat(response.getType())
                .isEqualTo(ProblemType.CODE);

        assertThat(response.getDescription())
                .isEqualTo(
                        "두 정수를 입력받아 두 수의 합을 출력하세요."
                );

        assertThat(response.getStarterCode())
                .isEqualTo("public class Main {\n}");

        assertThat(response.getRunningTimeLimit())
                .isEqualTo(RunningTimeLimit.SECOND_1);

        assertThat(response.getRunningMemoryLimit())
                .isEqualTo(RunningMemoryLimit.MB_128);

        assertThat(response.getTimerPolicy())
                .isEqualTo(
                        TimerPolicy.NOT_APPLY_TIMEPOLICY
                );

        assertThat(response.getSource())
                .isEqualTo(ProblemSource.HUMAN_AUTHORED);

        assertThat(response.getProblemStatus())
                .isEqualTo(ProblemStatus.DRAFT);

        assertThat(response.getCurrentVersionNo())
                .isEqualTo(1);

        assertThat(response.getLockVersion())
                .isEqualTo(0L);
    }

    @Test
    @DisplayName(
            "문제 정보를 수정하면 수정된 상태의 버전 스냅샷을 저장한다"
    )
    void updateProblem_success() {
        // given
        UUID problemId = UUID.randomUUID();

        Problem problem = Problem.create(
                "두 수의 합",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수를 입력받아 두 수의 합을 출력하세요.",
                "public class Main {\n}",
                RunningTimeLimit.SECOND_1,
                RunningMemoryLimit.MB_128,
                TimerPolicy.NOT_APPLY_TIMEPOLICY,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.PUBLISHED
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

        ProblemUpdateRequest request =
                mock(ProblemUpdateRequest.class);

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        when(request.getLockVersion())
                .thenReturn(0L);

        when(request.getTitle())
                .thenReturn("세 수의 합");

        when(request.getDifficulty())
                .thenReturn(ProblemDifficulty.MEDIUM);

        when(request.getStarterCode())
                .thenReturn(JsonNullable.undefined());

        // when
        ProblemResponse response =
                problemService.updateProblem(
                        problemId,
                        request
                );

        // then
        assertThat(response.getTitle())
                .isEqualTo("세 수의 합");

        assertThat(response.getDifficulty())
                .isEqualTo(ProblemDifficulty.MEDIUM);

        assertThat(response.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(response.getType())
                .isEqualTo(ProblemType.CODE);

        assertThat(response.getDescription())
                .isEqualTo(
                        "두 정수를 입력받아 두 수의 합을 출력하세요."
                );

        assertThat(response.getCurrentVersionNo())
                .isEqualTo(2);

        verify(problemRepository)
                .flush();

        ArgumentCaptor<ProblemVersion> versionCaptor =
                ArgumentCaptor.forClass(ProblemVersion.class);

        verify(problemVersionRepository)
                .save(versionCaptor.capture());

        ProblemVersion savedVersion =
                versionCaptor.getValue();

        assertThat(savedVersion.getProblemId())
                .isEqualTo(problemId);

        assertThat(savedVersion.getVersionNo())
                .isEqualTo(2);

        assertThat(
                savedVersion.getProblemSnapshot()
                        .get("title")
                        .asText()
        )
                .isEqualTo("세 수의 합");

        assertThat(
                savedVersion.getProblemSnapshot()
                        .get("difficulty")
                        .asText()
        )
                .isEqualTo(ProblemDifficulty.MEDIUM.name());

        assertThat(
                savedVersion.getProblemSnapshot()
                        .get("runningTimeLimit")
                        .asText()
        )
                .isEqualTo(RunningTimeLimit.SECOND_1.name());

        assertThat(
                savedVersion.getProblemSnapshot()
                        .get("runningMemoryLimit")
                        .asText()
        )
                .isEqualTo(RunningMemoryLimit.MB_128.name());
    }
}
