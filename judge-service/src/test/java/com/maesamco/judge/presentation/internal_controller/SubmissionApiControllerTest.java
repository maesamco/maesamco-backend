package com.maesamco.judge.presentation.internal_controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maesamco.judge.application.command_service.SubmissionCommandService;
import com.maesamco.judge.application.query_service.SubmissionQueryService;
import com.maesamco.judge.application.result.SubmissionCreateResult;
import com.maesamco.judge.application.result.SubmissionExternalGetResult;
import com.maesamco.judge.domain.entity.SubmissionResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.global.config.SecurityConfig;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.presentation.api_controller.SubmissionApiController;
import com.maesamco.judge.presentation.request.SubmissionCreateRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SubmissionApiController.class)
@Import(SubmissionApiControllerTest.TestSecurityConfig.class)
@AutoConfigureMockMvc
class SubmissionApiControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(
                            (request, response, authException) ->
                                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubmissionCommandService submissionCommandService;

    @MockitoBean
    private SubmissionQueryService submissionQueryService;

    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedAs(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Nested
    @DisplayName("POST /api/v1/submissions")
    class Create {

        @Test
        @DisplayName("새 제출이면 202와 함께 submissionId·PENDING 상태를 반환한다")
        void returns202WhenCreated() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID submissionId = UUID.randomUUID();
            SubmissionCreateRequest request = new SubmissionCreateRequest(
                    UUID.randomUUID(), "public class Main {}", "JAVA17");
            given(submissionCommandService.submit(any()))
                    .willReturn(SubmissionCreateResult.created(submissionId, SubmissionStatus.PENDING));

            mockMvc.perform(post("/api/v1/submissions")
                            .with(authenticatedAs(userId))
                            .header("Idempotency-Key", "idem-key-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.submissionId").value(submissionId.toString()))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("동일 Idempotency-Key로 이미 존재하는 제출이면 200을 반환한다")
        void returns200WhenExisting() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID submissionId = UUID.randomUUID();
            SubmissionCreateRequest request = new SubmissionCreateRequest(
                    UUID.randomUUID(), "public class Main {}", "JAVA17");
            given(submissionCommandService.submit(any()))
                    .willReturn(SubmissionCreateResult.existing(submissionId, SubmissionStatus.PENDING));

            mockMvc.perform(post("/api/v1/submissions")
                            .with(authenticatedAs(userId))
                            .header("Idempotency-Key", "idem-key-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.submissionId").value(submissionId.toString()));
        }

        @Test
        @DisplayName("problemId가 없으면 400을 반환한다")
        void returns400WhenProblemIdMissing() throws Exception {
            UUID userId = UUID.randomUUID();
            String invalidBody = """
                { "code": "public class Main {}", "language": "JAVA17" }
                """;

            mockMvc.perform(post("/api/v1/submissions")
                            .with(authenticatedAs(userId))
                            .header("Idempotency-Key", "idem-key-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("code가 비어있으면 400을 반환한다")
        void returns400WhenCodeBlank() throws Exception {
            UUID userId = UUID.randomUUID();
            SubmissionCreateRequest request = new SubmissionCreateRequest(UUID.randomUUID(), "", "JAVA17");

            mockMvc.perform(post("/api/v1/submissions")
                            .with(authenticatedAs(userId))
                            .header("Idempotency-Key", "idem-key-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 없으면 400을 반환한다")
        void returns400WhenIdempotencyKeyMissing() throws Exception {
            UUID userId = UUID.randomUUID();
            SubmissionCreateRequest request = new SubmissionCreateRequest(
                    UUID.randomUUID(), "public class Main {}", "JAVA17");

            mockMvc.perform(post("/api/v1/submissions")
                            .with(authenticatedAs(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("존재하지 않는 문제면 404를 반환한다")
        void returns404WhenProblemNotFound() throws Exception {
            UUID userId = UUID.randomUUID();
            SubmissionCreateRequest request = new SubmissionCreateRequest(
                    UUID.randomUUID(), "public class Main {}", "JAVA17");
            given(submissionCommandService.submit(any()))
                    .willThrow(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

            mockMvc.perform(post("/api/v1/submissions")
                            .with(authenticatedAs(userId))
                            .header("Idempotency-Key", "idem-key-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("동일 키로 다른 요청 바디가 오면 409를 반환한다")
        void returns409WhenIdempotencyKeyConflict() throws Exception {
            UUID userId = UUID.randomUUID();
            SubmissionCreateRequest request = new SubmissionCreateRequest(
                    UUID.randomUUID(), "public class Main {}", "JAVA17");
            given(submissionCommandService.submit(any()))
                    .willThrow(new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));

            mockMvc.perform(post("/api/v1/submissions")
                            .with(authenticatedAs(userId))
                            .header("Idempotency-Key", "idem-key-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("인증 정보가 없으면 401을 반환한다")
        void returns401WhenUnauthenticated() throws Exception {
            SubmissionCreateRequest request = new SubmissionCreateRequest(
                    UUID.randomUUID(), "public class Main {}", "JAVA17");

            mockMvc.perform(post("/api/v1/submissions")
                            .header("Idempotency-Key", "idem-key-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/submissions/{submissionId}")
    class GetSubmission {

        @Test
        @DisplayName("본인 제출 조회에 성공하면 200과 success/data 포맷으로 응답한다")
        void returns200WithSubmission() throws Exception {
            UUID submissionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            SubmissionExternalGetResult result = new SubmissionExternalGetResult(
                    submissionId, UUID.randomUUID(), UUID.randomUUID(), 1,
                    SubmissionStatus.COMPLETED, SubmissionResult.CORRECT, null,
                    List.of(new SubmissionExternalGetResult.TestResultItem(
                            UUID.randomUUID(), true, true, "8")),
                    120, 15360, Instant.now(), Instant.now()
            );
            given(submissionQueryService.getSubmission(submissionId, userId)).willReturn(result);

            mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId)
                            .with(authenticatedAs(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.testResults[0].passed").value(true));
        }

        @Test
        @DisplayName("존재하지 않거나 본인 제출이 아니면 404를 반환한다")
        void returns404WhenNotFoundOrNotOwned() throws Exception {
            UUID submissionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            given(submissionQueryService.getSubmission(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

            mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId)
                            .with(authenticatedAs(userId)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("인증 정보가 없으면 401을 반환한다")
        void returns401WhenUnauthenticated() throws Exception {
            UUID submissionId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId))
                    .andExpect(status().isUnauthorized());
        }
    }
}