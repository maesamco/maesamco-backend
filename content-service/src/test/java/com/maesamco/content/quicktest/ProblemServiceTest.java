package com.maesamco.content.quicktest;

import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.application.service.ProblemService;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.ProblemVersion;
import com.maesamco.content.problem.domain.enums.*;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemFinder problemFinder;

    @Mock
    private ProblemVersionRepository problemVersionRepository;

    @InjectMocks
    private ProblemService problemService;


    @Test
    @DisplayName("문제 단건 조회 시 문제 정보를 반환한다.")
    void getProblem_success() {

        // given
        UUID problemId = UUID.randomUUID();

        Problem problem = mock(Problem.class);

        when(problemFinder.getProblem(problemId)).thenReturn(problem);
        when(problem.getId()).thenReturn(problemId);
        when(problem.getTitle()).thenReturn("두 수의 합");
        when(problem.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(problem.getDifficulty()).thenReturn(ProblemDifficulty.EASY);
        when(problem.getType()).thenReturn(ProblemType.CODE);
        when(problem.getDescription()).thenReturn("두 정수를 입력받아 두 수의 합을 출력하세요.");
        when(problem.getStarterCode()).thenReturn("public class Main {\n}");
        when(problem.getRunningTimeLimit()).thenReturn(1000);
        when(problem.getRunningMemoryLimit()).thenReturn(128);
        when(problem.getTimerPolicy()).thenReturn(TimerPolicy.NOT_APPLY_TIMEPOLICY);
        when(problem.getSource()).thenReturn(ProblemSource.HUMAN_AUTHORED);
        when(problem.getProblemStatus()).thenReturn(ProblemStatus.DRAFT);
        when(problem.getCurrentVersionNo()).thenReturn(1);

        // when
        ProblemResponse response = problemService.getProblem(problemId);

        // then
        assertThat(response.getId()).isEqualTo(problemId);
        assertThat(response.getTitle()).isEqualTo("두 수의 합");
        assertThat(response.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
        assertThat(response.getDifficulty()).isEqualTo(ProblemDifficulty.EASY);
        assertThat(response.getType()).isEqualTo(ProblemType.CODE);
        assertThat(response.getDescription()).isEqualTo("두 정수를 입력받아 두 수의 합을 출력하세요.");
        assertThat(response.getStarterCode()).isEqualTo("public class Main {\n}");
        assertThat(response.getRunningTimeLimit()).isEqualTo(1000);
        assertThat(response.getRunningMemoryLimit()).isEqualTo(128);
        assertThat(response.getTimerPolicy()).isEqualTo(TimerPolicy.NOT_APPLY_TIMEPOLICY);
        assertThat(response.getSource()).isEqualTo(ProblemSource.HUMAN_AUTHORED);
        assertThat(response.getProblemStatus()).isEqualTo(ProblemStatus.DRAFT);
        assertThat(response.getCurrentVersionNo()).isEqualTo(1);

        System.out.println("===== 문제 단건 조회 결과 =====");
        System.out.println("problemId = " + response.getId());
        System.out.println("title = " + response.getTitle());
        System.out.println("language = " + response.getLanguage());
        System.out.println("difficulty = " + response.getDifficulty());
        System.out.println("type = " + response.getType());
        System.out.println("description = " + response.getDescription());
        System.out.println("starterCode = " + response.getStarterCode());
        System.out.println("runningTimeLimit = " + response.getRunningTimeLimit());
        System.out.println("runningMemoryLimit = " + response.getRunningMemoryLimit());
        System.out.println("timerPolicy = " + response.getTimerPolicy());
        System.out.println("source = " + response.getSource());
        System.out.println("problemStatus = " + response.getProblemStatus());
        System.out.println("currentVersionNo = " + response.getCurrentVersionNo());
    }


    @Test
    @DisplayName("문제 정보를 수정한다.")
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
                RunningTimeLimit.SECOND_1.getSeconds(),
                RunningMemoryLimit.MB_128.getMegabytes(),
                TimerPolicy.NOT_APPLY_TIMEPOLICY,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.PUBLISHED,
                1
        );

        ProblemUpdateRequest request = mock(ProblemUpdateRequest.class);

        when(problemFinder.getProblem(problemId)).thenReturn(problem);
        when(request.getTitle()).thenReturn("세 수의 합");
        when(request.getDifficulty()).thenReturn(ProblemDifficulty.MEDIUM);
        when(request.getStarterCode()).thenReturn(JsonNullable.undefined());

        // 수정 전
        System.out.println("===== 문제 수정 전 =====");
        System.out.println("title = " + problem.getTitle());
        System.out.println("language = " + problem.getLanguage());
        System.out.println("difficulty = " + problem.getDifficulty());
        System.out.println("type = " + problem.getType());
        System.out.println("description = " + problem.getDescription());
        System.out.println("starterCode = " + problem.getStarterCode());
        System.out.println("runningTimeLimit = " + problem.getRunningTimeLimit());
        System.out.println("runningMemoryLimit = " + problem.getRunningMemoryLimit());
        System.out.println("timerPolicy = " + problem.getTimerPolicy());
        System.out.println("source = " + problem.getSource());
        System.out.println("problemStatus = " + problem.getProblemStatus());
        System.out.println("currentVersionNo = " + problem.getCurrentVersionNo());

        // JPA 저장을 하지 않는 단위 테스트이므로 ID 직접 주입
        ReflectionTestUtils.setField(problem, "id", problemId);

        // when
        problemService.updateProblem(problemId, request);

        // then
        assertThat(problem.getTitle()).isEqualTo("세 수의 합");
        assertThat(problem.getDifficulty()).isEqualTo(ProblemDifficulty.MEDIUM);

        assertThat(problem.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
        assertThat(problem.getType()).isEqualTo(ProblemType.CODE);
        assertThat(problem.getDescription())
                .isEqualTo("두 정수를 입력받아 두 수의 합을 출력하세요.");

        System.out.println();
        System.out.println("===== 문제 수정 후 =====");
        System.out.println("title = " + problem.getTitle());
        System.out.println("language = " + problem.getLanguage());
        System.out.println("difficulty = " + problem.getDifficulty());
        System.out.println("type = " + problem.getType());
        System.out.println("description = " + problem.getDescription());
        System.out.println("starterCode = " + problem.getStarterCode());
        System.out.println("runningTimeLimit = " + problem.getRunningTimeLimit());
        System.out.println("runningMemoryLimit = " + problem.getRunningMemoryLimit());
        System.out.println("timerPolicy = " + problem.getTimerPolicy());
        System.out.println("source = " + problem.getSource());
        System.out.println("problemStatus = " + problem.getProblemStatus());
        System.out.println("currentVersionNo = " + problem.getCurrentVersionNo());


        ArgumentCaptor<ProblemVersion> versionCaptor =
                ArgumentCaptor.forClass(ProblemVersion.class);

        verify(problemVersionRepository).save(versionCaptor.capture());

        ProblemVersion savedVersion = versionCaptor.getValue();

        System.out.println();
        System.out.println("===== 생성된 문제 버전 =====");
        System.out.println("problemId = " + savedVersion.getProblemId());
        System.out.println("versionNo = " + savedVersion.getVersionNo());
        System.out.println("contentSnapshot = " + savedVersion.getProblemSnapshot());
    }
}