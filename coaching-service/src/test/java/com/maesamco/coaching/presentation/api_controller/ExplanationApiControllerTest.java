package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.ExplanationGenerationFacade;
import com.maesamco.coaching.application.query_service.ExplanationQueryService;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HintApiControllerTest와 동일한 이유(PR #70 리뷰, 용현님 P3) — Facade/QueryService 단위
 * 테스트만으로는 Security 설정이나 Controller의 HTTP Status 매핑을 잡지 못한다.
 */
@WebMvcTest(ExplanationApiController.class)
@Import(ExplanationApiControllerTest.TestSecurityConfig.class)
class ExplanationApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExplanationGenerationFacade explanationGenerationFacade;

    @MockitoBean
    private ExplanationQueryService explanationQueryService;

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    private final UUID submissionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private static RequestPostProcessor asUser(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    @Test
    @DisplayName("인증되지 않은 설명 등록은 401을 반환한다")
    void registerExplanation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/explanations", submissionId)
                        .contentType("application/json")
                        .content("{\"content\":\"설명\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("인증되지 않은 설명 조회도 401을 반환한다")
    void getExplanation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/explanations", submissionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("설명 등록에 성공하면 201과 함께 역질문을 반환한다")
    void registerExplanation_success_returns201() throws Exception {
        Explanation explanation = Explanation.create(UUID.randomUUID(), submissionId, "이 코드는 반복문으로 배열을 순회합니다.");
        FollowUpQuestion followUpQuestion = FollowUpQuestion.create(UUID.randomUUID(), "종료 조건은?", "경계값");
        when(explanationGenerationFacade.registerExplanation(submissionId, "이 코드는 반복문으로 배열을 순회합니다.", userId))
                .thenReturn(new ExplanationGenerationFacade.ExplanationRegistrationResult(explanation, followUpQuestion, true));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/explanations", submissionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"content\":\"이 코드는 반복문으로 배열을 순회합니다.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("이 코드는 반복문으로 배열을 순회합니다."))
                .andExpect(jsonPath("$.data.followUpQuestion.questionText").value("종료 조건은?"));
    }

    @Test
    @DisplayName("AI 역질문 생성이 실패해도 설명 등록 자체는 201이고 followUpQuestion은 null이다")
    void registerExplanation_followUpQuestionGenerationFailed_stillReturns201WithNullFollowUp() throws Exception {
        Explanation explanation = Explanation.create(UUID.randomUUID(), submissionId, "설명");
        when(explanationGenerationFacade.registerExplanation(submissionId, "설명", userId))
                .thenReturn(new ExplanationGenerationFacade.ExplanationRegistrationResult(explanation, null, true));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/explanations", submissionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"content\":\"설명\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.followUpQuestion").doesNotExist());
    }

    /**
     * 재교차검증 리뷰 대응 — 이미 등록된 설명에 대한 재요청(재시도, 중복 클릭 등)은
     * ExplanationGenerationFacade.retryExistingExplanation()이 처리하고 created=false로
     * 돌아온다. 컨트롤러는 이걸 201이 아니라 200으로 응답해야 한다 —
     * HintApiController.requestHint()와 같은 패턴.
     */
    @Test
    @DisplayName("이미 등록된 설명에 대한 재요청이면 201이 아니라 200을 반환한다")
    void registerExplanation_alreadyRegistered_returns200NotCreated() throws Exception {
        Explanation explanation = Explanation.create(UUID.randomUUID(), submissionId, "설명");
        FollowUpQuestion followUpQuestion = FollowUpQuestion.create(UUID.randomUUID(), "종료 조건은?", "경계값");
        when(explanationGenerationFacade.registerExplanation(submissionId, "설명", userId))
                .thenReturn(new ExplanationGenerationFacade.ExplanationRegistrationResult(explanation, followUpQuestion, false));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/explanations", submissionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"content\":\"설명\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followUpQuestion.questionText").value("종료 조건은?"));
    }

    @Test
    @DisplayName("본인 소유가 아닌 제출이면 404를 반환한다")
    void registerExplanation_otherUsersSubmission_returns404() throws Exception {
        when(explanationGenerationFacade.registerExplanation(submissionId, "설명", userId))
                .thenThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/explanations", submissionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"content\":\"설명\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("정답 상태가 아닌 제출이면 403을 반환한다")
    void registerExplanation_notCorrect_returns403() throws Exception {
        when(explanationGenerationFacade.registerExplanation(submissionId, "설명", userId))
                .thenThrow(new BusinessException(ErrorCode.EXPLANATION_NOT_ALLOWED));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/explanations", submissionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"content\":\"설명\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EXPLANATION_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("이미 같은 제출에 설명이 등록돼있으면 409를 반환한다")
    void registerExplanation_alreadyExists_returns409() throws Exception {
        when(explanationGenerationFacade.registerExplanation(submissionId, "설명", userId))
                .thenThrow(new BusinessException(ErrorCode.EXPLANATION_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/explanations", submissionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"content\":\"설명\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EXPLANATION_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("설명 조회는 SuccessResponse 구조로 반환한다")
    void getExplanation_returnsSuccessResponseStructure() throws Exception {
        Explanation explanation = Explanation.create(UUID.randomUUID(), submissionId, "설명 본문");
        ExplanationQueryService.ExplanationQueryResult result =
                new ExplanationQueryService.ExplanationQueryResult(explanation, null, null);
        when(explanationQueryService.getExplanation(submissionId, userId)).thenReturn(result);

        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/explanations", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("설명 본문"))
                .andExpect(jsonPath("$.data.followUpAnswer").doesNotExist());
    }

    @Test
    @DisplayName("등록된 설명이 없으면 404를 반환한다")
    void getExplanation_notFound_returns404() throws Exception {
        when(explanationQueryService.getExplanation(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.EXPLANATION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/explanations", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXPLANATION_NOT_FOUND"));
    }
}
