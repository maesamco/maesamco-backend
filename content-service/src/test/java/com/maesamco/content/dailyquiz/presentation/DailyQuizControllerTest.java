package com.maesamco.content.dailyquiz.presentation;

import com.maesamco.content.dailyquiz.application.query.DailyQuizGetQuery;
import com.maesamco.content.dailyquiz.application.query_service.DailyQuizGetQueryService;
import com.maesamco.content.dailyquiz.application.result.DailyQuizGetResult;
import com.maesamco.content.dailyquiz.application.result.DailyQuizQuestionGetResult;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptStatus;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizProblemType;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 오늘의 Daily Quiz 조회 API 계약과 인증 정책을 검증합니다.
 */
@WebMvcTest(DailyQuizController.class)
@Import(DailyQuizControllerTest.TestSecurityConfig.class)
class DailyQuizControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyQuizGetQueryService queryService;

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Test
    void 오늘의_세트가_없으면_404와_QUIZ_NOT_FOUND를_반환한다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(queryService.get(any(DailyQuizGetQuery.class)))
                .thenThrow(new BusinessException(ErrorCode.QUIZ_NOT_FOUND));

        mockMvc.perform(get("/api/v1/daily-quiz").with(asUser(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("QUIZ_NOT_FOUND"));
    }

    @Test
    void 조회_응답에_정답과_허용_답안_목록을_노출하지_않는다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        DailyQuizQuestionGetResult question = new DailyQuizQuestionGetResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                1,
                DailyQuizProblemType.MULTIPLE_CHOICE,
                "올바른 답을 선택하세요.",
                List.of("A", "B", "C", "D"),
                false,
                null
        );
        DailyQuizGetResult result = new DailyQuizGetResult(
                attemptId,
                DailyQuizAttemptStatus.IN_PROGRESS,
                3,
                "QUICK_ANSWER",
                180,
                Instant.parse("2026-09-11T03:00:00Z"),
                List.of(question)
        );
        when(queryService.get(any(DailyQuizGetQuery.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/daily-quiz").with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizAttemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.data.questions[0].prompt").value("올바른 답을 선택하세요."))
                .andExpect(jsonPath("$.data.questions[0].answer").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].allowedAnswerVariants").doesNotExist());
    }

    @Test
    void 미인증_요청은_차단한다() throws Exception {
        mockMvc.perform(get("/api/v1/daily-quiz"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(queryService);
    }

    private static RequestPostProcessor asUser(UUID userId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                )
        );
    }
}
