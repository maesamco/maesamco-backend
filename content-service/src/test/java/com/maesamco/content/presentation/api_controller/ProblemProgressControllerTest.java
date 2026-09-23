package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.ProblemProgressService;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProblemProgressController.class)
@Import({
        ProblemProgressControllerTest.TestSecurityConfig.class,
        JacksonConfig.class
})
class ProblemProgressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemProgressService problemProgressService;

    private final UUID userId = UUID.randomUUID();

    private final UUID problemId = UUID.randomUUID();

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
                            auth -> auth.anyRequest()
                                    .permitAll()
                    );

            return http.build();
        }
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
    @DisplayName("인증된 사용자가 전체 문제 풀이 이력을 조회하면 페이징된 결과를 반환한다")
    void getProblemProgresses_withoutStatus_returnsPagedProgresses()
            throws Exception {

        // given
        ProblemProgress firstProgress = createWrongProgress(
                UUID.randomUUID(),
                Instant.parse("2026-09-21T01:00:00Z")
        );

        ProblemProgress secondProgress = createCorrectProgress(
                UUID.randomUUID(),
                Instant.parse("2026-09-20T01:00:00Z")
        );

        Pageable pageable = PageRequest.of(
                1,
                2
        );

        when(problemProgressService.getProblemProgresses(
                eq(userId),
                isNull(),
                any(Pageable.class)
        )).thenReturn(
                new PageImpl<>(
                        List.of(
                                firstProgress,
                                secondProgress
                        ),
                        pageable,
                        5
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problem-progress"
                        )
                                .with(
                                        asUser(userId)
                                )
                                .param(
                                        "page",
                                        "1"
                                )
                                .param(
                                        "size",
                                        "2"
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
                        jsonPath("$.data.content.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.data.content[0].problemId")
                                .value(
                                        firstProgress.getProblemId().toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.content[0].versionNo")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.content[0].attemptNo")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.content[0].progressStatus")
                                .value("WRONG")
                )
                .andExpect(
                        jsonPath("$.data.content[1].problemId")
                                .value(
                                        secondProgress.getProblemId().toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.content[1].progressStatus")
                                .value("CORRECT")
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
                                .value(5)
                )
                .andExpect(
                        jsonPath("$.data.totalPages")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.data.hasNext")
                                .value(true)
                );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(problemProgressService)
                .getProblemProgresses(
                        eq(userId),
                        isNull(),
                        pageableCaptor.capture()
                );

        Pageable capturedPageable =
                pageableCaptor.getValue();

        assertThat(
                capturedPageable.getPageNumber()
        ).isEqualTo(1);

        assertThat(
                capturedPageable.getPageSize()
        ).isEqualTo(2);
    }

    @Test
    @DisplayName("progressStatus가 WRONG이면 WRONG 상태를 서비스에 전달하여 문제 풀이 이력을 조회한다")
    void getProblemProgresses_wrongStatus_returnsWrongProgresses()
            throws Exception {

        // given
        ProblemProgress problemProgress = createWrongProgress(
                problemId,
                Instant.parse("2026-09-21T01:00:00Z")
        );

        Pageable pageable = PageRequest.of(
                0,
                10
        );

        when(problemProgressService.getProblemProgresses(
                eq(userId),
                eq(ProblemProgressStatus.WRONG),
                any(Pageable.class)
        )).thenReturn(
                new PageImpl<>(
                        List.of(
                                problemProgress
                        ),
                        pageable,
                        1
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problem-progress"
                        )
                                .with(
                                        asUser(userId)
                                )
                                .param(
                                        "progressStatus",
                                        "WRONG"
                                )
                                .param(
                                        "page",
                                        "0"
                                )
                                .param(
                                        "size",
                                        "10"
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
                        jsonPath("$.data.content.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.content[0].problemId")
                                .value(
                                        problemId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.content[0].progressStatus")
                                .value("WRONG")
                )
                .andExpect(
                        jsonPath("$.data.content[0].createdAt")
                                .value(
                                        "2026-09-21T01:00:00Z"
                                )
                );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(problemProgressService)
                .getProblemProgresses(
                        eq(userId),
                        eq(ProblemProgressStatus.WRONG),
                        pageableCaptor.capture()
                );

        Pageable capturedPageable =
                pageableCaptor.getValue();

        assertThat(
                capturedPageable.getPageNumber()
        ).isZero();

        assertThat(
                capturedPageable.getPageSize()
        ).isEqualTo(10);
    }

    @Test
    @DisplayName("progressStatus가 CORRECT이면 CORRECT 상태를 서비스에 전달하여 문제 풀이 이력을 조회한다")
    void getProblemProgresses_correctStatus_returnsCorrectProgresses()
            throws Exception {

        // given
        Instant judgedAt =
                Instant.parse(
                        "2026-09-21T02:00:00Z"
                );

        ProblemProgress problemProgress =
                createCorrectProgress(
                        problemId,
                        judgedAt
                );

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        when(problemProgressService.getProblemProgresses(
                eq(userId),
                eq(ProblemProgressStatus.CORRECT),
                any(Pageable.class)
        )).thenReturn(
                new PageImpl<>(
                        List.of(
                                problemProgress
                        ),
                        pageable,
                        1
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problem-progress"
                        )
                                .with(
                                        asUser(userId)
                                )
                                .param(
                                        "progressStatus",
                                        "CORRECT"
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
                        jsonPath("$.data.content[0].problemId")
                                .value(
                                        problemId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.content[0].progressStatus")
                                .value("CORRECT")
                )
                .andExpect(
                        jsonPath("$.data.content[0].solvedAt")
                                .value(
                                        judgedAt.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.content[0].createdAt")
                                .value(
                                        judgedAt.toString()
                                )
                );

        verify(problemProgressService)
                .getProblemProgresses(
                        eq(userId),
                        eq(ProblemProgressStatus.CORRECT),
                        any(Pageable.class)
                );
    }

    @Test
    @DisplayName("page와 size를 전달하지 않으면 기본 페이징 값을 서비스에 전달한다")
    void getProblemProgresses_withoutPageParameters_usesDefaultPageable()
            throws Exception {

        // given
        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        when(problemProgressService.getProblemProgresses(
                eq(userId),
                isNull(),
                any(Pageable.class)
        )).thenReturn(
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problem-progress"
                        )
                                .with(
                                        asUser(userId)
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(problemProgressService)
                .getProblemProgresses(
                        eq(userId),
                        isNull(),
                        pageableCaptor.capture()
                );

        Pageable capturedPageable =
                pageableCaptor.getValue();

        assertThat(
                capturedPageable.getPageNumber()
        ).isZero();

        assertThat(
                capturedPageable.getPageSize()
        ).isEqualTo(20);
    }

    @Test
    @DisplayName("문제 풀이 이력이 없으면 빈 PageResponse를 반환한다")
    void getProblemProgresses_empty_returnsEmptyPage()
            throws Exception {

        // given
        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        when(problemProgressService.getProblemProgresses(
                eq(userId),
                isNull(),
                any(Pageable.class)
        )).thenReturn(
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problem-progress"
                        )
                                .with(
                                        asUser(userId)
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
                        jsonPath("$.data.content.length()")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.data.page")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.data.size")
                                .value(20)
                )
                .andExpect(
                        jsonPath("$.data.totalElements")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.data.totalPages")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.data.hasNext")
                                .value(false)
                );
    }

    @Test
    @DisplayName("존재하지 않는 progressStatus를 전달하면 400을 반환한다")
    void getProblemProgresses_invalidStatus_returns400()
            throws Exception {

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problem-progress"
                        )
                                .with(
                                        asUser(userId)
                                )
                                .param(
                                        "progressStatus",
                                        "SOLVED"
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );

        verifyNoInteractions(
                problemProgressService
        );
    }

    @Test
    @DisplayName("인증되지 않은 사용자가 문제 풀이 이력 목록을 조회하면 403을 반환한다")
    void getProblemProgresses_unauthenticated_returns403()
            throws Exception {

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problem-progress"
                        )
                )
                .andExpect(
                        status().isForbidden()
                );

        verifyNoInteractions(
                problemProgressService
        );
    }

    @Test
    @DisplayName("인증된 사용자가 특정 문제의 풀이 이력을 조회하면 200을 반환한다")
    void getProblemProgress_returns200()
            throws Exception {

        // given
        Instant judgedAt =
                Instant.parse(
                        "2026-09-21T03:00:00Z"
                );

        ProblemProgress problemProgress =
                createCorrectProgress(
                        problemId,
                        judgedAt
                );

        when(problemProgressService.getProblemProgress(
                userId,
                problemId
        )).thenReturn(
                problemProgress
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problem-progress/{problemId}",
                                problemId
                        )
                                .with(
                                        asUser(userId)
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
                        jsonPath("$.data.problemId")
                                .value(
                                        problemId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.versionNo")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.attemptNo")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.progressStatus")
                                .value("CORRECT")
                )
                .andExpect(
                        jsonPath("$.data.solvedAt")
                                .value(
                                        judgedAt.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.createdAt")
                                .value(
                                        judgedAt.toString()
                                )
                );

        verify(problemProgressService)
                .getProblemProgress(
                        userId,
                        problemId
                );
    }

    @Test
    @DisplayName("특정 문제의 풀이 이력이 존재하지 않으면 404를 반환한다")
    void getProblemProgress_notFound_returns404()
            throws Exception {

        // given
        when(problemProgressService.getProblemProgress(
                userId,
                problemId
        )).thenThrow(
                new BusinessException(
                        ErrorCode.PROBLEM_PROGRESS_NOT_FOUND
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problem-progress/{problemId}",
                                problemId
                        )
                                .with(
                                        asUser(userId)
                                )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "PROBLEM_PROGRESS_NOT_FOUND"
                                )
                );

        verify(problemProgressService)
                .getProblemProgress(
                        userId,
                        problemId
                );
    }

    @Test
    @DisplayName("인증되지 않은 사용자가 특정 문제의 풀이 이력을 조회하면 403을 반환한다")
    void getProblemProgress_unauthenticated_returns403()
            throws Exception {

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problem-progress/{problemId}",
                                problemId
                        )
                )
                .andExpect(
                        status().isForbidden()
                );

        verifyNoInteractions(
                problemProgressService
        );
    }

    private ProblemProgress createWrongProgress(
            UUID problemId,
            Instant judgedAt
    ) {
        return ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.WRONG,
                judgedAt
        );
    }

    private ProblemProgress createCorrectProgress(
            UUID problemId,
            Instant judgedAt
    ) {
        return ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.CORRECT,
                judgedAt
        );
    }
}