package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.ProblemTagService;
import com.maesamco.content.application.result.TagResult;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProblemTagController.class)
@Import(ProblemTagControllerTest.TestSecurityConfig.class)
class ProblemTagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemTagService problemTagService;

    private final UUID problemId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
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
    @DisplayName("문제의 태그 목록을 공개 조회하면 200을 반환한다")
    void getProblemTags_returns200() throws Exception {
        // given
        PageResult<TagResult> response =
                new PageResult<>(
                        java.util.List.of(),
                        1,
                        5,
                        0
                );

        when(problemTagService.searchProblemTags(
                org.mockito.ArgumentMatchers.eq(problemId),
                org.mockito.ArgumentMatchers.any(PageQuery.class)
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problems/{problemId}/tags",
                                problemId
                        )
                                .param("page", "1")
                                .param("size", "5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5));

        ArgumentCaptor<PageQuery> captor =
                ArgumentCaptor.forClass(PageQuery.class);

        verify(problemTagService)
                .searchProblemTags(
                        org.mockito.ArgumentMatchers.eq(problemId),
                        captor.capture()
                );

        PageQuery capturedPageQuery =
                captor.getValue();

        assertThat(capturedPageQuery.page())
                .isEqualTo(1);

        assertThat(capturedPageQuery.size())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("ADMIN이 문제에 태그를 등록하면 201을 반환한다")
    void addTagToProblem_admin_returns201()
            throws Exception {

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/contents/problems/{problemId}/tags/{tagId}",
                                problemId,
                                tagId
                        )
                                .with(asAdmin(adminId))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(problemTagService)
                .addTagToProblem(
                        problemId,
                        tagId
                );
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 문제에 태그를 등록하면 403을 반환한다")
    void addTagToProblem_nonAdmin_returns403()
            throws Exception {

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/contents/problems/{problemId}/tags/{tagId}",
                                problemId,
                                tagId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(problemTagService);
    }

    @Test
    @DisplayName("ADMIN이 문제에서 태그를 제거하면 200을 반환한다")
    void removeTagFromProblem_admin_returns200()
            throws Exception {

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/contents/problems/{problemId}/tags/{tagId}",
                                problemId,
                                tagId
                        )
                                .with(asAdmin(adminId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(problemTagService)
                .removeTagFromProblem(
                        problemId,
                        tagId
                );
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 문제에서 태그를 제거하면 403을 반환한다")
    void removeTagFromProblem_nonAdmin_returns403()
            throws Exception {

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/contents/problems/{problemId}/tags/{tagId}",
                                problemId,
                                tagId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(problemTagService);
    }
}