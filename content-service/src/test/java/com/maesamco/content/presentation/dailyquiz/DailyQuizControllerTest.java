package com.maesamco.content.presentation.dailyquiz;

import com.maesamco.content.application.dailyquiz.command.DailyQuizSubmitCommand;
import com.maesamco.content.application.dailyquiz.query.DailyQuizGetQuery;
import com.maesamco.content.application.dailyquiz.query_service.DailyQuizGetQueryService;
import com.maesamco.content.application.dailyquiz.result.DailyQuizGetResult;
import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionGetResult;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSubmitResult;
import com.maesamco.content.application.dailyquiz.service.DailyQuizSubmitService;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttemptStatus;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import com.maesamco.content.global.config.OpenApiConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 오늘의 Daily Quiz 조회 API 계약과 인증 정책을 검증합니다.
 */
@WebMvcTest(controllers = DailyQuizController.class, properties = "springdoc.api-docs.enabled=true")
@Import({DailyQuizControllerTest.TestSecurityConfig.class, OpenApiConfig.class})
@ImportAutoConfiguration({
        SpringDocConfiguration.class,
        SpringDocWebMvcConfiguration.class,
        SpringDocSpecPropertiesConfiguration.class,
        SpringDocConfigProperties.class
})
class DailyQuizControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyQuizGetQueryService queryService;

    @MockitoBean
    private DailyQuizSubmitService submitService;

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

    @Test
    void 문항_제출은_인터페이스의_경로와_본문_매핑을_사용한다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID questionVersionId = UUID.randomUUID();
        when(submitService.submit(any(DailyQuizSubmitCommand.class)))
                .thenReturn(new DailyQuizSubmitResult(questionVersionId, true, false, null, null));

        mockMvc.perform(post("/api/v1/daily-quiz/{quizAttemptId}/questions/{questionVersionId}/submit",
                        attemptId, questionVersionId)
                        .with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"int\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionVersionId").value(questionVersionId.toString()))
                .andExpect(jsonPath("$.data.correct").value(true));

        verify(submitService).submit(new DailyQuizSubmitCommand(userId, attemptId, questionVersionId, "int"));
    }

    @Test
    void 미인증_문항_제출은_차단한다() throws Exception {
        mockMvc.perform(post("/api/v1/daily-quiz/{quizAttemptId}/questions/{questionVersionId}/submit",
                        UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"int\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(submitService);
    }

    @Test
    void 공백_답안은_인터페이스의_검증을_거쳐_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/daily-quiz/{quizAttemptId}/questions/{questionVersionId}/submit",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(asUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));

        verifyNoInteractions(submitService);
    }

    @Test
    void Swagger_문서에_조회와_제출_계약을_표시한다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/daily-quiz'].get.summary")
                        .value("오늘의 일일 퀴즈 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/daily-quiz'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/daily-quiz'].get.responses['404'].description")
                        .value("QUIZ_NOT_FOUND — 오늘 생성된 세트가 없음"))
                .andExpect(jsonPath("$.paths['/api/v1/daily-quiz'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/daily-quiz/{quizAttemptId}/questions/"
                        + "{questionVersionId}/submit'].post.summary")
                        .value("일일 퀴즈 문항 제출"))
                .andExpect(jsonPath("$.paths['/api/v1/daily-quiz/{quizAttemptId}/questions/"
                        + "{questionVersionId}/submit'].post.parameters.length()")
                        .value(2))
                .andExpect(jsonPath("$.paths['/api/v1/daily-quiz/{quizAttemptId}/questions/"
                        + "{questionVersionId}/submit'].post.parameters[?(@.name == 'quizAttemptId')].description")
                        .value("일일 퀴즈 세트 ID"))
                .andExpect(jsonPath("$.paths['/api/v1/daily-quiz/{quizAttemptId}/questions/"
                        + "{questionVersionId}/submit'].post.responses['409'].description")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.DailyQuizGetResponse.properties.totalCount.description")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.DailyQuizSubmitResponse.properties.summary.description")
                        .exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
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
