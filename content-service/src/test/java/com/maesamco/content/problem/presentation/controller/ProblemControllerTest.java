package com.maesamco.content.problem.presentation.controller;

import com.maesamco.content.global.config.JacksonConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.problem.application.service.ProblemService;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.enums.*;
import com.maesamco.content.problem.presentation.dto.request.ProblemCreateRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemSearchRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemUpdateRequest;
import com.maesamco.content.problem.presentation.dto.response.ProblemCreateResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemSearchItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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

    private final UUID problemId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    /**
     * 실제 SecurityConfig 전체를 가져오지 않고 Controller 테스트에 필요한
     * 최소 Security 설정만 사용한다.
     *
     * HTTP 레벨에서는 모든 요청을 통과시키되,
     * @EnableMethodSecurity를 통해 Controller의 @PreAuthorize는 실제로 검증한다.
     */
    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(
                HttpSecurity http
        ) throws Exception {

            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(
                            auth -> auth.anyRequest().permitAll()
                    );

            return http.build();
        }
    }

    private static RequestPostProcessor asAdmin(UUID adminId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        adminId,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        )
                )
        );
    }

    private static RequestPostProcessor asUser(UUID userId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER")
                        )
                )
        );
    }

    @Test
    @DisplayName("ADMIN이 문제를 생성하면 201과 생성된 문제 정보를 반환한다")
    void createProblem_admin_returns201() throws Exception {
        // given
        Problem problem = createProblem(
                problemId,
                "두 수의 합",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "public class Main {}",
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.REVIEW_PENDING
        );

        ProblemCreateResponse response =
                ProblemCreateResponse.from(problem);

        when(problemService.createProblem(
                any(ProblemCreateRequest.class)
        )).thenReturn(response);

        String json = """
                {
                    "title": "두 수의 합",
                    "language": "JAVA",
                    "difficulty": "EASY",
                    "type": "CODE",
                    "description": "두 정수를 입력받아 합을 출력하세요.",
                    "starterCode": "public class Main {}",
                    "runningTimeLimit": "%s",
                    "runningMemoryLimit": "%s",
                    "timerPolicy": "APPLY60",
                    "source": "HUMAN_AUTHORED"
                }
                """.formatted(
                RunningTimeLimit.values()[0].name(),
                RunningMemoryLimit.values()[0].name()
        );

        // when & then
        mockMvc.perform(
                        post("/api/v1/contents/problems")
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.id")
                                .value(problemId.toString())
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value("두 수의 합")
                );

        ArgumentCaptor<ProblemCreateRequest> captor =
                ArgumentCaptor.forClass(
                        ProblemCreateRequest.class
                );

        verify(problemService)
                .createProblem(captor.capture());

        ProblemCreateRequest request =
                captor.getValue();

        assertThat(request.getTitle())
                .isEqualTo("두 수의 합");

        assertThat(request.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(request.getTimerPolicy())
                .isEqualTo(TimerPolicy.APPLY60);
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 문제를 생성하면 403을 반환한다")
    void createProblem_nonAdmin_returns403() throws Exception {
        // given
        String json = """
                {
                    "title": "두 수의 합",
                    "language": "JAVA",
                    "difficulty": "MEDIUM",
                    "type": "CODE",
                    "description": "문제 지문",
                    "runningTimeLimit": "%s",
                    "runningMemoryLimit": "%s",
                    "timerPolicy": "APPLY120",
                    "source": "AI_ASSISTED"
                }
                """.formatted(
                RunningTimeLimit.values()[0].name(),
                RunningMemoryLimit.values()[0].name()
        );

        // when & then
        mockMvc.perform(
                        post("/api/v1/contents/problems")
                                .with(asUser(userId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(problemService);
    }

    @Test
    @DisplayName("문제 생성 시 필수값이 누락되면 400을 반환한다")
    void createProblem_invalidRequest_returns400()
            throws Exception {

        // given
        // title을 의도적으로 생략
        String json = """
                {
                    "language": "PYTHON",
                    "difficulty": "EASY",
                    "type": "CODE",
                    "description": "문제 지문",
                    "runningTimeLimit": "%s",
                    "runningMemoryLimit": "%s",
                    "timerPolicy": "APPLY180",
                    "source": "HUMAN_AUTHORED"
                }
                """.formatted(
                RunningTimeLimit.values()[0].name(),
                RunningMemoryLimit.values()[0].name()
        );

        // when & then
        mockMvc.perform(
                        post("/api/v1/contents/problems")
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(problemService);
    }

    @Test
    @DisplayName("문제를 단건 조회하면 200과 문제 상세 정보를 반환한다")
    void getProblem_returns200() throws Exception {
        // given
        Problem problem = createProblem(
                problemId,
                "문자열 뒤집기",
                ProgrammingLanguage.PYTHON,
                ProblemDifficulty.MEDIUM,
                ProblemType.CODE,
                "def solution():",
                TimerPolicy.APPLY120,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.PUBLISHED
        );

        when(problemService.getProblem(problemId))
                .thenReturn(
                        ProblemResponse.from(problem)
                );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.id")
                                .value(problemId.toString())
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value("문자열 뒤집기")
                )
                .andExpect(
                        jsonPath("$.data.language")
                                .value("PYTHON")
                )
                .andExpect(
                        jsonPath("$.data.difficulty")
                                .value("MEDIUM")
                )
                .andExpect(
                        jsonPath("$.data.timerPolicy")
                                .value("APPLY120")
                )
                .andExpect(
                        jsonPath("$.data.problemStatus")
                                .value("PUBLISHED")
                );
    }

    @Test
    @DisplayName("존재하지 않는 문제를 조회하면 404를 반환한다")
    void getProblem_notFound_returns404() throws Exception {
        // given
        when(problemService.getProblem(problemId))
                .thenThrow(
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
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("PROBLEM_NOT_FOUND")
                );
    }

    @Test
    @DisplayName("검색 조건과 페이징 정보를 전달하면 문제 목록을 반환한다")
    void getProblems_returnsPagedProblems()
            throws Exception {

        // given
        UUID javaProblemId = UUID.randomUUID();
        UUID pythonProblemId = UUID.randomUUID();

        Problem javaProblem = createProblem(
                javaProblemId,
                "Java 반복문",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "public class Main {}",
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.PUBLISHED
        );

        Problem pythonProblem = createProblem(
                pythonProblemId,
                "Python 반복문",
                ProgrammingLanguage.PYTHON,
                ProblemDifficulty.HARD,
                ProblemType.CODE,
                "def solution():",
                TimerPolicy.APPLY300,
                ProblemSource.AI_ASSISTED,
                ProblemStatus.PUBLISHED
        );

        List<ProblemSearchItemResponse> content =
                List.of(
                        ProblemSearchItemResponse.from(javaProblem),
                        ProblemSearchItemResponse.from(pythonProblem)
                );

        Page<ProblemSearchItemResponse> page =
                new PageImpl<>(
                        content,
                        PageRequest.of(1, 2),
                        4
                );

        PageResponse<ProblemSearchItemResponse> response =
                PageResponse.from(page);

        when(problemService.searchProblems(
                any(ProblemSearchRequest.class),
                any(Pageable.class)
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/contents/problems")
                                .param("language", "JAVA")
                                .param("difficulty", "EASY")
                                .param("type", "CODE")
                                .param(
                                        "problemStatus",
                                        "PUBLISHED"
                                )
                                .param(
                                        "source",
                                        "HUMAN_AUTHORED"
                                )
                                .param("page", "1")
                                .param("size", "2")
                                .param("sort", "title")
                                .param("direction", "asc")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.content.length()")
                                .value(2)
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
                        jsonPath("$.data.totalElements")
                                .value(4)
                )
                .andExpect(
                        jsonPath("$.data.totalPages")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.data.hasNext")
                                .value(false)
                );

        ArgumentCaptor<ProblemSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        ProblemSearchRequest.class
                );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(problemService)
                .searchProblems(
                        requestCaptor.capture(),
                        pageableCaptor.capture()
                );

        ProblemSearchRequest searchRequest =
                requestCaptor.getValue();

        assertThat(searchRequest.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(searchRequest.getDifficulty())
                .isEqualTo(ProblemDifficulty.EASY);

        assertThat(searchRequest.getType())
                .isEqualTo(ProblemType.CODE);

        assertThat(searchRequest.getProblemStatus())
                .isEqualTo(ProblemStatus.PUBLISHED);

        assertThat(searchRequest.getSource())
                .isEqualTo(ProblemSource.HUMAN_AUTHORED);

        Pageable capturedPageable =
                pageableCaptor.getValue();

        assertThat(capturedPageable.getPageNumber())
                .isEqualTo(1);

        assertThat(capturedPageable.getPageSize())
                .isEqualTo(2);

        assertThat(
                capturedPageable
                        .getSort()
                        .getOrderFor("title")
        ).isNotNull();

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

        // when & then
        mockMvc.perform(
                        get("/api/v1/contents/problems")
                                .param(
                                        "difficulty",
                                        "IMPOSSIBLE"
                                )
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(problemService);
    }

    @Test
    @DisplayName("ADMIN이 문제를 수정하면 200과 수정된 문제를 반환한다")
    void updateProblem_admin_returns200() throws Exception {
        // given
        Problem updatedProblem = createProblem(
                problemId,
                "수정된 문제",
                ProgrammingLanguage.CPP,
                ProblemDifficulty.HARD,
                ProblemType.CODE,
                null,
                TimerPolicy.APPLY300,
                ProblemSource.AI_ASSISTED,
                ProblemStatus.REVIEW_PENDING
        );

        updatedProblem.increaseVersion();

        when(problemService.updateProblem(
                any(UUID.class),
                any(ProblemUpdateRequest.class)
        )).thenReturn(
                ProblemResponse.from(updatedProblem)
        );

        /*
         * starterCode를 null로 명시적으로 전달한다.
         *
         * JsonNullableModule이 정상 등록돼있다면
         * isPresent() == true이면서 내부 값은 null이어야 한다.
         */
        String json = """
            {
                "title": "수정된 문제",
                "difficulty": "HARD",
                "starterCode": null,
                "timerPolicy": "APPLY300",
                "source": "AI_ASSISTED"
            }
            """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.title")
                                .value("수정된 문제")
                )
                .andExpect(
                        jsonPath("$.data.difficulty")
                                .value("HARD")
                )
                .andExpect(
                        jsonPath("$.data.timerPolicy")
                                .value("APPLY300")
                )
                .andExpect(
                        jsonPath("$.data.source")
                                .value("AI_ASSISTED")
                )
                .andExpect(
                        jsonPath("$.data.currentVersionNo")
                                .value(2)
                );

        ArgumentCaptor<ProblemUpdateRequest> captor =
                ArgumentCaptor.forClass(
                        ProblemUpdateRequest.class
                );

        verify(problemService)
                .updateProblem(
                        org.mockito.ArgumentMatchers.eq(problemId),
                        captor.capture()
                );

        ProblemUpdateRequest request =
                captor.getValue();

        assertThat(request.getTitle())
                .isEqualTo("수정된 문제");

        assertThat(request.getDifficulty())
                .isEqualTo(ProblemDifficulty.HARD);

        assertThat(request.getTimerPolicy())
                .isEqualTo(TimerPolicy.APPLY300);

        assertThat(request.getSource())
                .isEqualTo(ProblemSource.AI_ASSISTED);

        // 명시적 null은 "전달된 값"으로 취급
        assertThat(request.getStarterCode())
                .isNotNull();

        assertThat(
                request.getStarterCode().isPresent()
        ).isTrue();

        assertThat(
                request.getStarterCode().orElse("default")
        ).isNull();
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 문제를 수정하면 403을 반환한다")
    void updateProblem_nonAdmin_returns403()
            throws Exception {

        // given
        String json = """
                {
                    "title": "수정 시도"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(asUser(userId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(problemService);
    }

    @Test
    @DisplayName("ADMIN이 문제를 삭제하면 200을 반환하고 인증 사용자 ID를 서비스에 전달한다")
    void deleteProblem_admin_returns200()
            throws Exception {

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(asAdmin(adminId))
                )
                .andExpect(status().isOk())
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

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/contents/problems/{problemId}",
                                problemId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(problemService);
    }

    private Problem createProblem(
            UUID id,
            String title,
            ProgrammingLanguage language,
            ProblemDifficulty difficulty,
            ProblemType type,
            String starterCode,
            TimerPolicy timerPolicy,
            ProblemSource source,
            ProblemStatus problemStatus
    ) {
        Problem problem = Problem.create(
                title,
                language,
                difficulty,
                type,
                title + " 문제 지문입니다.",
                starterCode,
                RunningTimeLimit.values()[0],
                RunningMemoryLimit.values()[0],
                timerPolicy,
                source,
                problemStatus
        );

        ReflectionTestUtils.setField(
                problem,
                "id",
                id
        );

        return problem;
    }
}