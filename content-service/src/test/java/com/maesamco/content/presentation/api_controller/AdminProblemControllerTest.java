package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.persistence_service.ProblemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import com.maesamco.content.application.persistence_service.ProblemService;
import com.maesamco.content.application.query.ProblemSearchQuery;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProblemController.class)
@Import(AdminProblemControllerTest.TestSecurityConfig.class)
class AdminProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemPublicationFacade problemPublicationFacade;

    @MockitoBean
    private ProblemService problemService;

    private final UUID problemId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(
                            auth -> auth.anyRequest().permitAll()
                    );

            return http.build();
        }
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
