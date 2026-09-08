package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.FollowUpAnswerFacade;
import com.maesamco.coaching.domain.entity.CoachingSessionStatus;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ExplanationApiControllerTest/HintApiControllerTest와 동일한 이유(PR #70 리뷰, 용현님 P3;
 * PR #98 자가 리뷰, 용현님 P2로 이 컨트롤러도 뒤늦게 추가) — Facade 단위 테스트만으로는
 * Security 설정이나 Controller의 HTTP Status·검증 매핑을 잡지 못한다.
 */
@WebMvcTest(FollowUpAnswerApiController.class)
@Import(FollowUpAnswerApiControllerTest.TestSecurityConfig.class)
class FollowUpAnswerApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowUpAnswerFacade followUpAnswerFacade;

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    private final UUID followUpQuestionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private static RequestPostProcessor asUser(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    private FollowUpAnswer answer(String answerText) {
        FollowUpAnswer answer = FollowUpAnswer.create(followUpQuestionId, answerText);
        ReflectionTestUtils.setField(answer, "id", UUID.randomUUID());
        return answer;
    }

    @Test
    @DisplayName("인증되지 않은 답변 등록은 401을 반환한다")
    void registerAnswer_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/coaching/follow-up-questions/{followUpQuestionId}/answers", followUpQuestionId)
                        .contentType("application/json")
                        .content("{\"answerText\":\"답변\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));

        verifyNoInteractions(followUpAnswerFacade);
    }

    @Test
    @DisplayName("답변 등록에 성공하면 201과 함께 완료된 세션 상태를 반환한다")
    void registerAnswer_success_returns201() throws Exception {
        when(followUpAnswerFacade.registerAnswer(followUpQuestionId, "답변 내용", userId))
                .thenReturn(new FollowUpAnswerFacade.FollowUpAnswerRegisterResult(
                        answer("답변 내용"), CoachingSessionStatus.COMPLETED
                ));

        mockMvc.perform(post("/api/v1/coaching/follow-up-questions/{followUpQuestionId}/answers", followUpQuestionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"answerText\":\"답변 내용\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answerText").value("답변 내용"))
                .andExpect(jsonPath("$.data.coachingSessionStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("답변이 빈 문자열이면 400을 반환하고 Facade는 호출되지 않는다")
    void registerAnswer_blankAnswerText_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/coaching/follow-up-questions/{followUpQuestionId}/answers", followUpQuestionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"answerText\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));

        verifyNoInteractions(followUpAnswerFacade);
    }

    @Test
    @DisplayName("답변이 1000자를 초과하면 400을 반환하고 Facade는 호출되지 않는다")
    void registerAnswer_tooLongAnswerText_returns400() throws Exception {
        String tooLong = "가".repeat(1001);

        mockMvc.perform(post("/api/v1/coaching/follow-up-questions/{followUpQuestionId}/answers", followUpQuestionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"answerText\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));

        verifyNoInteractions(followUpAnswerFacade);
    }

    @Test
    @DisplayName("답변이 정확히 1000자면 통과한다")
    void registerAnswer_exactly1000Chars_passesValidation() throws Exception {
        String exactly1000 = "가".repeat(1000);
        when(followUpAnswerFacade.registerAnswer(followUpQuestionId, exactly1000, userId))
                .thenReturn(new FollowUpAnswerFacade.FollowUpAnswerRegisterResult(
                        answer(exactly1000), CoachingSessionStatus.COMPLETED
                ));

        mockMvc.perform(post("/api/v1/coaching/follow-up-questions/{followUpQuestionId}/answers", followUpQuestionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"answerText\":\"" + exactly1000 + "\"}"))
                .andExpect(status().isCreated());

        verify(followUpAnswerFacade).registerAnswer(followUpQuestionId, exactly1000, userId);
    }

    @Test
    @DisplayName("존재하지 않거나 본인 소유가 아닌 역질문이면 404를 반환한다")
    void registerAnswer_notOwnedOrNotFound_returns404() throws Exception {
        when(followUpAnswerFacade.registerAnswer(followUpQuestionId, "답변", userId))
                .thenThrow(new BusinessException(ErrorCode.FOLLOW_UP_QUESTION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/coaching/follow-up-questions/{followUpQuestionId}/answers", followUpQuestionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"answerText\":\"답변\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FOLLOW_UP_QUESTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 답변한 역질문이면 409를 반환한다")
    void registerAnswer_alreadyAnswered_returns409() throws Exception {
        when(followUpAnswerFacade.registerAnswer(followUpQuestionId, "답변", userId))
                .thenThrow(new BusinessException(ErrorCode.FOLLOW_UP_ANSWER_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/coaching/follow-up-questions/{followUpQuestionId}/answers", followUpQuestionId)
                        .with(asUser(userId))
                        .contentType("application/json")
                        .content("{\"answerText\":\"답변\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FOLLOW_UP_ANSWER_ALREADY_EXISTS"));
    }
}
