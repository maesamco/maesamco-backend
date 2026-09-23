package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.facade.ProblemPublicationFacade;
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

    private final UUID problemId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @TestConfiguration
    @EnableMethodSecurity
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