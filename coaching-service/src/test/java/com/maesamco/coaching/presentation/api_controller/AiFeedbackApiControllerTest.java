package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.AiFeedbackRetryFacade;
import com.maesamco.coaching.application.query_service.AiFeedbackQueryService;
import com.maesamco.coaching.domain.entity.AiFeedback;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ExplanationApiControllerTest와 동일한 이유(PR #70 리뷰, 용현님 P3) — Facade/QueryService
 * 단위 테스트만으로는 Security 설정이나 Controller의 HTTP Status 매핑을 잡지 못한다.
 */
@WebMvcTest(AiFeedbackApiController.class)
@Import(AiFeedbackApiControllerTest.TestSecurityConfig.class)
class AiFeedbackApiControllerTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiFeedbackQueryService aiFeedbackQueryService;

    @MockitoBean
    private AiFeedbackRetryFacade aiFeedbackRetryFacade;

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

    private AiFeedback feedback() {
        ArrayNode emptyArray = JSON_MAPPER.createArrayNode();
        return AiFeedback.create(
                UUID.randomUUID(), emptyArray, emptyArray, emptyArray, emptyArray, emptyArray, "다음엔 재귀를 연습해보세요."
        );
    }

    @Test
    @DisplayName("인증되지 않은 피드백 조회는 401을 반환한다")
    void getFeedback_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/feedback", submissionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("인증되지 않은 재시도 요청도 401을 반환한다")
    void retryFeedback_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/feedback/retry", submissionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("피드백 조회에 성공하면 200과 함께 피드백을 반환한다")
    void getFeedback_success_returns200() throws Exception {
        AiFeedback feedback = feedback();
        when(aiFeedbackQueryService.getFeedback(submissionId, userId)).thenReturn(feedback);

        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/feedback", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nextDirection").value("다음엔 재귀를 연습해보세요."));
    }

    @Test
    @DisplayName("아직 생성된 피드백이 없으면 404를 반환한다")
    void getFeedback_notFound_returns404() throws Exception {
        when(aiFeedbackQueryService.getFeedback(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.AI_FEEDBACK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/feedback", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AI_FEEDBACK_NOT_FOUND"));
    }

    @Test
    @DisplayName("본인 소유가 아닌 제출을 조회하면 404를 반환한다")
    void getFeedback_otherUsersSubmission_returns404() throws Exception {
        when(aiFeedbackQueryService.getFeedback(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/feedback", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("재검증(PR #111) — 세션이 아직 완료되지 않았으면 조회는 404(AI_FEEDBACK_NOT_STARTED)를 반환한다")
    void getFeedback_sessionNotCompleted_returns404NotStarted() throws Exception {
        when(aiFeedbackQueryService.getFeedback(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.AI_FEEDBACK_NOT_STARTED));

        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/feedback", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AI_FEEDBACK_NOT_STARTED"));
    }

    @Test
    @DisplayName("재검증(PR #111) — 재시도 예산이 소진됐으면 조회도 409(AI_FEEDBACK_RETRY_LIMIT_EXCEEDED)를 반환한다")
    void getFeedback_retryExhausted_returns409() throws Exception {
        when(aiFeedbackQueryService.getFeedback(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.AI_FEEDBACK_RETRY_LIMIT_EXCEEDED));

        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/feedback", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AI_FEEDBACK_RETRY_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("재시도에 성공하면 200과 함께 새 피드백을 반환한다")
    void retryFeedback_success_returns200() throws Exception {
        AiFeedback feedback = feedback();
        when(aiFeedbackRetryFacade.retryFeedback(submissionId, userId)).thenReturn(feedback);

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/feedback/retry", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nextDirection").value("다음엔 재귀를 연습해보세요."));
    }

    @Test
    @DisplayName("이미 피드백이 존재하면 재시도는 409를 반환한다")
    void retryFeedback_alreadyExists_returns409() throws Exception {
        when(aiFeedbackRetryFacade.retryFeedback(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.AI_FEEDBACK_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/feedback/retry", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AI_FEEDBACK_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("재시도 횟수를 초과하면 409를 반환한다")
    void retryFeedback_limitExceeded_returns409() throws Exception {
        when(aiFeedbackRetryFacade.retryFeedback(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.AI_FEEDBACK_RETRY_LIMIT_EXCEEDED));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/feedback/retry", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AI_FEEDBACK_RETRY_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("재시도해도 여전히 실패하면 404를 반환한다")
    void retryFeedback_stillFails_returns404() throws Exception {
        when(aiFeedbackRetryFacade.retryFeedback(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.AI_FEEDBACK_NOT_FOUND));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/feedback/retry", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AI_FEEDBACK_NOT_FOUND"));
    }

    @Test
    @DisplayName("재검증(PR #111) — 동시 재시도 요청이 락을 못 얻으면 409(AI_FEEDBACK_RETRY_IN_PROGRESS)를 반환한다")
    void retryFeedback_alreadyInProgress_returns409() throws Exception {
        when(aiFeedbackRetryFacade.retryFeedback(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.AI_FEEDBACK_RETRY_IN_PROGRESS));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/feedback/retry", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AI_FEEDBACK_RETRY_IN_PROGRESS"));
    }
}
