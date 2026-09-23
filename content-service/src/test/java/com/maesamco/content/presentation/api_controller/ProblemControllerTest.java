package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.command.ProblemCreateCommand;
import com.maesamco.content.application.command.ProblemUpdateCommand;
import com.maesamco.content.application.query.ProblemSearchQuery;
import com.maesamco.content.application.result.ProblemResult;
import com.maesamco.content.application.result.ProblemSearchResult;
import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.persistence_service.ProblemService;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.*;
import com.maesamco.content.global.config.JacksonConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProblemController.class)
@Import({
        ProblemControllerTest.TestSecurityConfig.class,
        JacksonConfig.class
})
class ProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemService problemService;

    @MockitoBean
    private ProblemPublicationFacade problemPublicationService;

    private final UUID problemId =
            UUID.randomUUID();

    private final UUID adminId =
            UUID.randomUUID();

    private final UUID userId =
            UUID.randomUUID();

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(
                HttpSecurity http
        ) throws Exception {

            http.csrf(
                            AbstractHttpConfigurer::disable
                    )
                    .authorizeHttpRequests(
                            auth ->
                                    auth.anyRequest()
                                            .permitAll()
                    );

            return http.build();
        }
    }

    private static RequestPostProcessor asAdmin(
            UUID adminId
    ) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        adminId,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_ADMIN"
                                )
                        )
                )
        );
    }

    private static RequestPostProcessor asUser(
            UUID userId
    ) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_USER"
                                )
                        )
                )
        );
    }

    @Test
    @DisplayName(
            "문제 수정 중 실제 낙관적 락 충돌이 발생하면 HTTP 409와 범용 에러코드를 반환한다"
    )
    void updateProblem_optimisticLockConflict_returns409()
            throws Exception {

        when(
                problemService.updateProblem(
                        eq(problemId),
                        any(ProblemUpdateCommand.class)
                )
        ).thenThrow(
                new ObjectOptimisticLockingFailureException(
                        Problem.class,
                        problemId
                )
        );

        String json = """
            {
                "lockVersion": 0,
                "title": "동시 수정"
            }
            """;

        mockMvc.perform(
                        patch(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content(json)
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "RESOURCE_MODIFIED_CONCURRENTLY"
                                )
                );
    }

    @Test
    @DisplayName(
            "문제 삭제 중 실제 낙관적 락 충돌이 발생하면 HTTP 409와 범용 에러코드를 반환한다"
    )
    void deleteProblem_optimisticLockConflict_returns409()
            throws Exception {

        doThrow(
                new ObjectOptimisticLockingFailureException(
                        Problem.class,
                        problemId
                )
        ).when(
                problemService
        ).deleteProblem(
                problemId,
                adminId
        );

        mockMvc.perform(
                        delete(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "RESOURCE_MODIFIED_CONCURRENTLY"
                                )
                );
    }

    @Test
    @DisplayName("ADMIN은 비공개 상태의 문제도 관리자 단건 조회 API로 조회할 수 있다")
    void getProblemForAdmin_admin_returns200()
            throws Exception {

        // given
        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        when(
                problemService.getProblemForAdmin(
                        problemId
                )
        ).thenReturn(
                ProblemResult.from(problem)
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problems/admin/{problemId}",
                                problemId
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.id")
                                .value(
                                        problemId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value(
                                        "두 수의 합"
                                )
                );

        verify(problemService)
                .getProblemForAdmin(
                        problemId
                );
    }


    @Test
    @DisplayName("ADMIN이 아닌 사용자는 관리자 문제 단건 조회 API를 호출할 수 없다")
    void getProblemForAdmin_nonAdmin_returns403()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/contents/problems/admin/{problemId}",
                                problemId
                        )
                                .with(
                                        asUser(userId)
                                )
                )
                .andExpect(
                        status().isForbidden()
                );

        verifyNoInteractions(
                problemService
        );
    }

    @Test
    @DisplayName("ADMIN이 문제를 생성하면 Request를 Command로 변환하여 서비스에 전달한다")
    void createProblem_admin_returns201()
            throws Exception {

        // given
        Problem problem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        when(problemService.createProblem(
                any(ProblemCreateCommand.class)
        )).thenReturn(
                ProblemResult.from(problem)
        );

        String json = """
                {
                    "title": "두 수의 합",
                    "language": "JAVA",
                    "difficulty": "EASY",
                    "type": "CODE",
                    "description": "두 정수를 입력받아 합을 출력하세요.",
                    "starterCode": "public class Main {}",
                    "runningTimeLimit": "SECOND_1",
                    "runningMemoryLimit": "MB_128",
                    "timerPolicy": "APPLY60",
                    "source": "HUMAN_AUTHORED"
                }
                """;

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/contents/problems"
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content(json)
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.id")
                                .value(
                                        problemId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value(
                                        "두 수의 합"
                                )
                );

        ArgumentCaptor<ProblemCreateCommand> captor =
                ArgumentCaptor.forClass(
                        ProblemCreateCommand.class
                );

        verify(problemService)
                .createProblem(
                        captor.capture()
                );

        ProblemCreateCommand command =
                captor.getValue();

        assertThat(command.getTitle())
                .isEqualTo(
                        "두 수의 합"
                );

        assertThat(command.getLanguage())
                .isEqualTo(
                        ProgrammingLanguage.JAVA
                );

        assertThat(command.getDifficulty())
                .isEqualTo(
                        ProblemDifficulty.EASY
                );

        assertThat(command.getTimerPolicy())
                .isEqualTo(
                        TimerPolicy.APPLY60
                );

        assertThat(command.getSource())
                .isEqualTo(
                        ProblemSource.HUMAN_AUTHORED
                );
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 문제를 생성하면 403을 반환한다")
    void createProblem_nonAdmin_returns403()
            throws Exception {

        String json = """
                {
                    "title": "두 수의 합",
                    "language": "JAVA",
                    "difficulty": "EASY",
                    "type": "CODE",
                    "description": "문제 지문",
                    "runningTimeLimit": "SECOND_1",
                    "runningMemoryLimit": "MB_128",
                    "timerPolicy": "APPLY60",
                    "source": "HUMAN_AUTHORED"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/contents/problems"
                        )
                                .with(
                                        asUser(userId)
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content(json)
                )
                .andExpect(
                        status().isForbidden()
                );

        verifyNoInteractions(
                problemService
        );
    }

    @Test
    @DisplayName("문제 생성 필수값이 누락되면 400을 반환한다")
    void createProblem_invalidRequest_returns400()
            throws Exception {

        String json = """
                {
                    "language": "JAVA",
                    "difficulty": "EASY",
                    "type": "CODE",
                    "description": "문제 지문",
                    "runningTimeLimit": "SECOND_1",
                    "runningMemoryLimit": "MB_128",
                    "timerPolicy": "APPLY60",
                    "source": "HUMAN_AUTHORED"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/contents/problems"
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content(json)
                )
                .andExpect(
                        status().isBadRequest()
                );

        verifyNoInteractions(
                problemService
        );
    }

    @Test
    @DisplayName("공개 문제를 단건 조회하면 200을 반환한다")
    void getProblem_returns200()
            throws Exception {

        // given
        Problem problem =
                createProblem(
                        ProblemStatus.PUBLISHED
                );

        when(problemService.getProblemForUser(
                problemId
        )).thenReturn(
                ProblemResult.from(problem)
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.id")
                                .value(
                                        problemId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value(
                                        "두 수의 합"
                                )
                )
                .andExpect(
                        jsonPath("$.data.language")
                                .value(
                                        "JAVA"
                                )
                )
                .andExpect(
                        jsonPath("$.data.difficulty")
                                .value(
                                        "EASY"
                                )
                );

        verify(problemService)
                .getProblemForUser(
                        problemId
                );
    }

    @Test
    @DisplayName("존재하지 않는 문제를 조회하면 404를 반환한다")
    void getProblem_notFound_returns404()
            throws Exception {

        // given
        when(problemService.getProblemForUser(
                problemId
        )).thenThrow(
                new BusinessException(
                        ErrorCode.PROBLEM_NOT_FOUND
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "PROBLEM_NOT_FOUND"
                                )
                );
    }

    @Test
    @DisplayName("검색 Request를 Query로 변환하고 Pageable과 함께 서비스에 전달한다")
    void getProblems_returnsPagedProblems()
            throws Exception {

        // given
        ProblemSearchResult searchResult =
                ProblemSearchResult.from(
                        createProblem(
                                ProblemStatus.PUBLISHED
                        )
                );

        Pageable pageable =
                PageRequest.of(
                        1,
                        2
                );

        when(problemService.searchProblems(
                any(ProblemSearchQuery.class),
                any(Pageable.class)
        )).thenReturn(
                new PageImpl<>(
                        List.of(
                                searchResult
                        ),
                        pageable,
                        3
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problems"
                        )
                                .param(
                                        "language",
                                        "JAVA"
                                )
                                .param(
                                        "difficulty",
                                        "EASY"
                                )
                                .param(
                                        "type",
                                        "CODE"
                                )
                                .param(
                                        "source",
                                        "HUMAN_AUTHORED"
                                )
                                .param(
                                        "page",
                                        "1"
                                )
                                .param(
                                        "size",
                                        "2"
                                )
                                .param(
                                        "sort",
                                        "title"
                                )
                                .param(
                                        "direction",
                                        "asc"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath(
                                "$.data.content.length()"
                        ).value(1)
                )
                .andExpect(
                        jsonPath("$.data.page")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.size")
                                .value(2)
                )
                .andExpect(
                        jsonPath(
                                "$.data.totalElements"
                        ).value(3)
                );

        ArgumentCaptor<ProblemSearchQuery> queryCaptor =
                ArgumentCaptor.forClass(
                        ProblemSearchQuery.class
                );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(problemService)
                .searchProblems(
                        queryCaptor.capture(),
                        pageableCaptor.capture()
                );

        ProblemSearchQuery query =
                queryCaptor.getValue();

        assertThat(query.getLanguage())
                .isEqualTo(
                        ProgrammingLanguage.JAVA
                );

        assertThat(query.getDifficulty())
                .isEqualTo(
                        ProblemDifficulty.EASY
                );

        assertThat(query.getType())
                .isEqualTo(
                        ProblemType.CODE
                );

        assertThat(query.getSource())
                .isEqualTo(
                        ProblemSource.HUMAN_AUTHORED
                );

        Pageable capturedPageable =
                pageableCaptor.getValue();

        assertThat(
                capturedPageable.getPageNumber()
        ).isEqualTo(1);

        assertThat(
                capturedPageable.getPageSize()
        ).isEqualTo(2);

        assertThat(
                capturedPageable
                        .getSort()
                        .getOrderFor("title")
                        .isAscending()
        ).isTrue();
    }

    @Test
    @DisplayName("잘못된 enum 검색 조건을 전달하면 400을 반환한다")
    void getProblems_invalidEnum_returns400()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/contents/problems"
                        )
                                .param(
                                        "difficulty",
                                        "IMPOSSIBLE"
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );

        verifyNoInteractions(
                problemService
        );
    }

    @Test
    @DisplayName("ADMIN이 문제를 수정하면 Request를 Command로 변환하여 전달한다")
    void updateProblem_admin_returns200()
            throws Exception {

        // given
        Problem updatedProblem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        updatedProblem.changeTitle(
                "수정된 문제"
        );

        updatedProblem.changeDifficulty(
                ProblemDifficulty.HARD
        );

        updatedProblem.increaseVersion();

        UUID lessonId = UUID.randomUUID();
        updatedProblem.changeLessonId(lessonId);

        when(problemService.updateProblem(
                eq(problemId),
                any(ProblemUpdateCommand.class)
        )).thenReturn(
                ProblemResult.from(
                        updatedProblem
                )
        );

        String json = """
                {
                    "lockVersion": 0,
                    "title": "수정된 문제",
                    "difficulty": "HARD",
                    "starterCode": null,
                    "timerPolicy": "APPLY60",
                    "source": "HUMAN_AUTHORED"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content(json)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value(
                                        "수정된 문제"
                                )
                )
                .andExpect(
                        jsonPath("$.data.difficulty")
                                .value(
                                        "HARD"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.data.currentVersionNo"
                        ).value(2)
                )
                .andExpect(
                        jsonPath("$.data.lessonId")
                                .value(lessonId.toString())
                );

        ArgumentCaptor<ProblemUpdateCommand> captor =
                ArgumentCaptor.forClass(
                        ProblemUpdateCommand.class
                );

        verify(problemService)
                .updateProblem(
                        eq(problemId),
                        captor.capture()
                );

        ProblemUpdateCommand command =
                captor.getValue();

        assertThat(
                command.getLockVersion()
        ).isEqualTo(0L);

        assertThat(
                command.getTitle()
        ).isEqualTo(
                "수정된 문제"
        );

        assertThat(
                command.getDifficulty()
        ).isEqualTo(
                ProblemDifficulty.HARD
        );

        /*
         * Presentation의 JsonNullable은
         * Application의 UpdateField로 변환되어 전달되어야 한다.
         *
         * "starterCode": null은 필드가 요청에 포함된 상태이므로
         * defined=true이고 실제 value는 null이어야 한다.
         */
        assertThat(
                command.getStarterCode()
        ).isNotNull();

        assertThat(
                command.getStarterCode()
                        .isDefined()
        ).isTrue();

        assertThat(
                command.getStarterCode()
                        .getValue()
        ).isNull();
    }

    @Test
    @DisplayName("starterCode가 수정 요청에 없으면 undefined 상태로 Command에 전달한다")
    void updateProblem_starterCodeUndefined_passesUndefinedField()
            throws Exception {

        // given
        Problem updatedProblem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        when(problemService.updateProblem(
                eq(problemId),
                any(ProblemUpdateCommand.class)
        )).thenReturn(
                ProblemResult.from(
                        updatedProblem
                )
        );

        String json = """
                {
                    "lockVersion": 0,
                    "title": "수정된 문제"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content(json)
                )
                .andExpect(
                        status().isOk()
                );

        ArgumentCaptor<ProblemUpdateCommand> captor =
                ArgumentCaptor.forClass(
                        ProblemUpdateCommand.class
                );

        verify(problemService)
                .updateProblem(
                        eq(problemId),
                        captor.capture()
                );

        ProblemUpdateCommand command =
                captor.getValue();

        /*
         * starterCode가 JSON 요청에 아예 없으면
         * Application에는 UpdateField.undefined() 상태로 전달한다.
         */
        assertThat(
                command.getStarterCode()
        ).isNotNull();

        assertThat(
                command.getStarterCode()
                        .isDefined()
        ).isFalse();

        assertThat(
                command.getStarterCode()
                        .getValue()
        ).isNull();
    }

    @Test
    @DisplayName("starterCode를 명시적으로 null로 전달하면 defined 상태와 null 값을 Command에 전달한다")
    void updateProblem_starterCodeNull_passesDefinedNullField()
            throws Exception {

        // given
        Problem updatedProblem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        when(problemService.updateProblem(
                eq(problemId),
                any(ProblemUpdateCommand.class)
        )).thenReturn(
                ProblemResult.from(
                        updatedProblem
                )
        );

        String json = """
                {
                    "lockVersion": 0,
                    "starterCode": null
                }
                """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content(json)
                )
                .andExpect(
                        status().isOk()
                );

        ArgumentCaptor<ProblemUpdateCommand> captor =
                ArgumentCaptor.forClass(
                        ProblemUpdateCommand.class
                );

        verify(problemService)
                .updateProblem(
                        eq(problemId),
                        captor.capture()
                );

        ProblemUpdateCommand command =
                captor.getValue();

        /*
         * starterCode가 명시적으로 null이면
         * 필드는 전달된 상태이므로 defined=true이다.
         */
        assertThat(
                command.getStarterCode()
        ).isNotNull();

        assertThat(
                command.getStarterCode()
                        .isDefined()
        ).isTrue();

        assertThat(
                command.getStarterCode()
                        .getValue()
        ).isNull();
    }

    @Test
    @DisplayName("starterCode에 실제 값을 전달하면 defined 상태와 값을 Command에 전달한다")
    void updateProblem_starterCodeValue_passesDefinedField()
            throws Exception {

        // given
        Problem updatedProblem =
                createProblem(
                        ProblemStatus.REVIEW_PENDING
                );

        when(problemService.updateProblem(
                eq(problemId),
                any(ProblemUpdateCommand.class)
        )).thenReturn(
                ProblemResult.from(
                        updatedProblem
                )
        );

        String json = """
                {
                    "lockVersion": 0,
                    "starterCode": "public class UpdatedMain {}"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content(json)
                )
                .andExpect(
                        status().isOk()
                );

        ArgumentCaptor<ProblemUpdateCommand> captor =
                ArgumentCaptor.forClass(
                        ProblemUpdateCommand.class
                );

        verify(problemService)
                .updateProblem(
                        eq(problemId),
                        captor.capture()
                );

        ProblemUpdateCommand command =
                captor.getValue();

        /*
         * 실제 starterCode 값이 전달되면
         * defined=true이며 전달된 값을 그대로 보존한다.
         */
        assertThat(
                command.getStarterCode()
        ).isNotNull();

        assertThat(
                command.getStarterCode()
                        .isDefined()
        ).isTrue();

        assertThat(
                command.getStarterCode()
                        .getValue()
        ).isEqualTo(
                "public class UpdatedMain {}"
        );
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 문제를 수정하면 403을 반환한다")
    void updateProblem_nonAdmin_returns403()
            throws Exception {

        String json = """
                {
                    "lockVersion": 0,
                    "title": "수정 시도"
                }
                """;

        mockMvc.perform(
                        patch(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(
                                        asUser(userId)
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content(json)
                )
                .andExpect(
                        status().isForbidden()
                );

        verifyNoInteractions(
                problemService
        );
    }

    @Test
    @DisplayName("ADMIN이 문제를 삭제하면 사용자 ID를 서비스에 전달한다")
    void deleteProblem_admin_returns200()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        verify(problemService)
                .deleteProblem(
                        problemId,
                        adminId
                );
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 문제를 삭제하면 403을 반환한다")
    void deleteProblem_nonAdmin_returns403()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(
                                        asUser(userId)
                                )
                )
                .andExpect(
                        status().isForbidden()
                );

        verifyNoInteractions(
                problemService
        );
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
                        "두 정수를 입력받아 합을 출력하세요.",
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
