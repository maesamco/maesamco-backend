package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.persistence_service.ProblemService;
import com.maesamco.content.application.persistence_service.ProblemVersionService;
import com.maesamco.content.application.query.ProblemSearchQuery;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.ProblemVersionSnapshot;
import com.maesamco.content.support.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProblemController.class)
@Import(TestSecurityConfig.class)
class AdminProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemPublicationFacade problemPublicationFacade;

    @MockitoBean
    private ProblemService problemService;

    @MockitoBean
    private ProblemVersionService problemVersionService;

    private final UUID problemId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName(
            "ADMIN이 아닌 사용자는 문제 버전 이력을 조회할 수 없다"
    )
    void getProblemVersions_nonAdmin_returns403()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/admin/contents/problems/{problemId}/versions",
                                problemId
                        )
                                .with(
                                        asUser(
                                                userId
                                        )
                                )
                )
                .andExpect(
                        status().isForbidden()
                );

        verifyNoInteractions(
                problemVersionService
        );
    }

    @Test
    @DisplayName(
            "ADMIN은 특정 문제의 특정 버전을 조회할 수 있다"
    )
    void getProblemVersion_admin_returns200()
            throws Exception {

        ProblemVersion problemVersion =
                mock(
                        ProblemVersion.class
                );

        ProblemVersionSnapshot snapshot =
                mock(
                        ProblemVersionSnapshot.class
                );

        UUID problemVersionId =
                UUID.randomUUID();

        when(
                problemVersion.getId()
        ).thenReturn(
                problemVersionId
        );

        when(
                problemVersion.getProblemId()
        ).thenReturn(
                problemId
        );

        when(
                problemVersion.getVersionNo()
        ).thenReturn(
                3
        );

        when(
                problemVersion.getPublishedAt()
        ).thenReturn(
                Instant.parse(
                        "2026-09-24T00:00:00Z"
                )
        );

        when(
                problemVersion.toVersionSnapshot()
        ).thenReturn(
                snapshot
        );

        when(
                problemVersionService
                        .getProblemVersion(
                                problemId,
                                3
                        )
        ).thenReturn(
                problemVersion
        );

        mockMvc.perform(
                        get(
                                "/api/v1/admin/contents/problems/{problemId}/versions/{versionNo}",
                                problemId,
                                3
                        )
                                .with(
                                        asAdmin(
                                                adminId
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.data.versionNo")
                                .value(3)
                );
    }

    @Test
    @DisplayName("ADMIN은 REVIEW_PENDING 상태의 문제를 검색할 수 있다")
    void searchProblems_adminWithStatus_returns200()
            throws Exception {

        when(
                problemService.searchProblemsForAdmin(
                        any(ProblemSearchQuery.class),
                        any(Pageable.class)
                )
        ).thenReturn(
                Page.empty()
        );

        mockMvc.perform(
                        get(
                                "/api/v1/admin/contents/problems"
                        )
                                .with(
                                        asAdmin(adminId)
                                )
                                .param(
                                        "problemStatus",
                                        "REVIEW_PENDING"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        ArgumentCaptor<ProblemSearchQuery>
                queryCaptor =
                ArgumentCaptor.forClass(
                        ProblemSearchQuery.class
                );

        verify(problemService)
                .searchProblemsForAdmin(
                        queryCaptor.capture(),
                        any(Pageable.class)
                );

        assertThat(
                queryCaptor.getValue()
                        .getProblemStatus()
        ).isEqualTo(
                ProblemStatus.REVIEW_PENDING
        );
    }

    @Test
    @DisplayName(
            "ADMIN은 특정 문제의 전체 버전 이력을 조회할 수 있다"
    )
    void getProblemVersions_admin_returns200()
            throws Exception {

        ProblemVersion problemVersion =
                mock(
                        ProblemVersion.class
                );

        ProblemVersionSnapshot snapshot =
                mock(
                        ProblemVersionSnapshot.class
                );

        UUID problemVersionId =
                UUID.randomUUID();

        when(
                problemVersion.getId()
        ).thenReturn(
                problemVersionId
        );

        when(
                problemVersion.getProblemId()
        ).thenReturn(
                problemId
        );

        when(
                problemVersion.getVersionNo()
        ).thenReturn(
                2
        );

        when(
                problemVersion.getPublishedAt()
        ).thenReturn(
                Instant.parse(
                        "2026-09-24T00:00:00Z"
                )
        );

        when(
                problemVersion.toVersionSnapshot()
        ).thenReturn(
                snapshot
        );

        when(
                problemVersionService
                        .getProblemVersions(
                                problemId
                        )
        ).thenReturn(
                List.of(
                        problemVersion
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/admin/contents/problems/{problemId}/versions",
                                problemId
                        )
                                .with(
                                        asAdmin(
                                                adminId
                                        )
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
                        jsonPath("$.data[0].id")
                                .value(
                                        problemVersionId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data[0].versionNo")
                                .value(2)
                );

        verify(problemVersionService)
                .getProblemVersions(
                        problemId
                );
    }

    @Test
    @DisplayName("ADMIN이 상태를 지정하지 않으면 전체 상태 검색 조건을 전달한다")
    void searchProblems_adminWithoutStatus_returns200()
            throws Exception {

        when(
                problemService.searchProblemsForAdmin(
                        any(ProblemSearchQuery.class),
                        any(Pageable.class)
                )
        ).thenReturn(
                Page.empty()
        );

        mockMvc.perform(
                        get(
                                "/api/v1/admin/contents/problems"
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

        ArgumentCaptor<ProblemSearchQuery>
                queryCaptor =
                ArgumentCaptor.forClass(
                        ProblemSearchQuery.class
                );

        verify(problemService)
                .searchProblemsForAdmin(
                        queryCaptor.capture(),
                        any(Pageable.class)
                );

        assertThat(
                queryCaptor.getValue()
                        .getProblemStatus()
        ).isNull();
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자는 관리자 문제 검색 API를 호출할 수 없다")
    void searchProblems_nonAdmin_returns403()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/admin/contents/problems"
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
    @DisplayName("ADMIN이 문제 발행을 승인하면 Facade를 호출하고 200을 반환한다")
    void approvePublication_admin_returns200() throws Exception {

        // when & then
        mockMvc.perform(
                        post("/api/v1/admin/contents/problems/{problemId}/approve", problemId)
                                .with(asAdmin(adminId))
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        verify(problemPublicationFacade)
                .approvePublication(problemId);
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 문제 발행을 승인하면 403을 반환한다")
    void approvePublication_nonAdmin_returns403() throws Exception {

        // when & then
        mockMvc.perform(
                        post("/api/v1/admin/contents/problems/{problemId}/approve", problemId)
                                .with(asUser(userId))
                )
                .andExpect(
                        status().isForbidden()
                );

        verifyNoInteractions(
                problemPublicationFacade
        );
    }

    @Test
    @DisplayName("이슈 #253 — ADMIN이 재발행을 요청하면 Facade를 호출하고 200을 반환한다")
    void revertToReviewPendingForRepublish_admin_returns200() throws Exception {

        // when & then
        mockMvc.perform(
                        post("/api/v1/admin/contents/problems/{problemId}/republish", problemId)
                                .with(asAdmin(adminId))
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        verify(problemPublicationFacade)
                .revertToReviewPendingForRepublish(problemId);
    }

    @Test
    @DisplayName("이슈 #253 — ADMIN이 아닌 사용자가 재발행을 요청하면 403을 반환한다")
    void revertToReviewPendingForRepublish_nonAdmin_returns403() throws Exception {

        // when & then
        mockMvc.perform(
                        post("/api/v1/admin/contents/problems/{problemId}/republish", problemId)
                                .with(asUser(userId))
                )
                .andExpect(
                        status().isForbidden()
                );

        verifyNoInteractions(
                problemPublicationFacade
        );
    }

    private static RequestPostProcessor asAdmin(UUID adminId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        adminId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    private static RequestPostProcessor asUser(UUID userId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }
}
