package com.maesamco.judge.presentation.api_controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.maesamco.judge.application.result.SubmissionSummaryResult;
import com.maesamco.judge.domain.entity.SubmissionResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.global.response.PageResponse;
import com.maesamco.judge.presentation.request.SubmissionCreateRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
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

    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedWithNullPrincipal() {
        return authentication(new UsernamePasswordAuthenticationToken(
                null, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
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
        @DisplayName("Authentication 자체가 없으면 Security 필터 단계에서 401을 반환한다")
        void returns401WhenNoAuthenticationPresent() throws Exception {
            SubmissionCreateRequest request = new SubmissionCreateRequest(
                    UUID.randomUUID(), "public class Main {}", "JAVA17");

            mockMvc.perform(post("/api/v1/submissions")
                            .header("Idempotency-Key", "idem-key-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Authentication은 있지만 principal이 null이면 컨트롤러의 requireAuthenticated()가 401을 반환한다")
        void returns401WhenAuthenticationPrincipalIsNull() throws Exception {
            SubmissionCreateRequest request = new SubmissionCreateRequest(
                    UUID.randomUUID(), "public class Main {}", "JAVA17");

            mockMvc.perform(post("/api/v1/submissions")
                            .with(authenticatedWithNullPrincipal())
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
        @DisplayName("Authentication 자체가 없으면 Security 필터 단계에서 401을 반환한다")
        void returns401WhenNoAuthenticationPresent() throws Exception {
            UUID submissionId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Authentication은 있지만 principal이 null이면 컨트롤러의 requireAuthenticated()가 401을 반환한다")
        void returns401WhenAuthenticationPrincipalIsNull() throws Exception {
            UUID submissionId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId)
                            .with(authenticatedWithNullPrincipal()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/submissions/me")
    class GetMySubmissions {

        @Test
        @DisplayName("본인 제출 이력을 페이지 형태로 반환한다")
        void returns200WithPageResponse() throws Exception {
            UUID userId = UUID.randomUUID();
            SubmissionSummaryResult summary = new SubmissionSummaryResult(
                    UUID.randomUUID(), UUID.randomUUID(), 1,
                    SubmissionStatus.COMPLETED, SubmissionResult.CORRECT, Instant.now());
            PageResponse<SubmissionSummaryResult> pageResponse =
                    new PageResponse<>(List.of(summary), 0, 20, 1, 1, false);

            given(submissionQueryService.getSubmissions(eq(userId), isNull(), any()))
                    .willReturn(pageResponse);

            mockMvc.perform(get("/api/v1/submissions/me")
                            .with(authenticatedAs(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("problemId 쿼리 파라미터를 서비스에 그대로 전달한다")
        void passesProblemIdFilter() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            PageResponse<SubmissionSummaryResult> emptyResponse =
                    new PageResponse<>(List.of(), 0, 20, 0, 0, false);

            given(submissionQueryService.getSubmissions(eq(userId), eq(problemId), any()))
                    .willReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/submissions/me")
                            .param("problemId", problemId.toString())
                            .with(authenticatedAs(userId)))
                    .andExpect(status().isOk());

            verify(submissionQueryService).getSubmissions(eq(userId), eq(problemId), any());
        }

        @Test
        @DisplayName("인증 정보가 없으면 401을 반환한다")
        void returns401WhenUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/submissions/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("/me가 {submissionId} 단건 조회로 잘못 라우팅되지 않는다")
        void routesToListNotSingleGet() throws Exception {
            UUID userId = UUID.randomUUID();
            PageResponse<SubmissionSummaryResult> emptyResponse =
                    new PageResponse<>(List.of(), 0, 20, 0, 0, false);
            given(submissionQueryService.getSubmissions(any(), any(), any())).willReturn(emptyResponse);

            // getSubmission()으로 잘못 갔다면 "me"를 UUID로 파싱하려다 400이 났을 것 — 200이면 제대로 라우팅된 것
            mockMvc.perform(get("/api/v1/submissions/me")
                            .with(authenticatedAs(userId)))
                    .andExpect(status().isOk());

            verify(submissionQueryService, never()).getSubmission(any(), any());
        }

        @Test
        @DisplayName("page/size/sort/direction 쿼리 파라미터가 Pageable에 정확히 반영된다")
        void passesPageableParametersCorrectly() throws Exception {
            UUID userId = UUID.randomUUID();
            PageResponse<SubmissionSummaryResult> emptyResponse =
                    new PageResponse<>(List.of(), 1, 10, 0, 0, false);

            given(submissionQueryService.getSubmissions(eq(userId), isNull(), any()))
                    .willReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/submissions/me")
                            .param("page", "1")
                            .param("size", "10")
                            .param("sort", "submittedAt")
                            .param("direction", "ASC")
                            .with(authenticatedAs(userId)))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(submissionQueryService).getSubmissions(eq(userId), isNull(), pageableCaptor.capture());
            Pageable captured = pageableCaptor.getValue();

            assertThat(captured.getPageNumber()).isEqualTo(1);
            assertThat(captured.getPageSize()).isEqualTo(10);
            assertThat(captured.getSort().getOrderFor("submittedAt")).isNotNull();
            assertThat(captured.getSort().getOrderFor("submittedAt").isAscending()).isTrue();
        }
    }
}