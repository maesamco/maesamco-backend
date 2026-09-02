package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.HintGenerationFacade;
import com.maesamco.coaching.application.query_service.HintQueryService;
import com.maesamco.coaching.domain.entity.Hint;
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
 * PR #70 리뷰(용현님 P3) — Facade 단위 테스트만으로는 Security 설정이나 Controller의
 * HTTP Status 매핑 변경을 잡지 못하므로, Controller 레벨 HTTP 계약을 별도로 검증한다.
 *
 * 실제 SecurityConfig(JWT PEM 로딩 등)를 그대로 임포트하지 않고, 이 테스트만을 위한 최소
 * 필터 체인(CSRF 비활성화 + permitAll)을 별도로 둔다 — 그렇지 않으면 SecurityFilterChain
 * 빈이 없어 Spring Boot의 기본 보안 자동 설정(폼 로그인 + CSRF)이 대신 켜져서, 검증하려는
 * 애플리케이션 로직(requireAuthenticated())보다 먼저 모든 요청을 403으로 막아버린다.
 * 인증 여부 자체는 이 필터 체인이 아니라 각 API의 @AuthenticationPrincipal이 null인지로만
 * 판단하므로(실제 SecurityConfig와 동일한 설계), permitAll이어도 검증 대상 로직은 그대로 탄다.
 */
@WebMvcTest(HintApiController.class)
@Import(HintApiControllerTest.TestSecurityConfig.class)
class HintApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HintGenerationFacade hintGenerationFacade;

    @MockitoBean
    private HintQueryService hintQueryService;

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
    @DisplayName("인증되지 않은 힌트 요청은 401을 반환한다")
    void requestHint_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/hints", submissionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("인증되지 않은 힌트 목록 조회도 401을 반환한다")
    void getHints_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/hints", submissionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("최초 힌트 생성은 201을 반환한다")
    void requestHint_created_returns201() throws Exception {
        Hint hint = Hint.create(UUID.randomUUID(), 1, "1단계 힌트");
        HintGenerationFacade.HintGenerationResult result =
                new HintGenerationFacade.HintGenerationResult(UUID.randomUUID(), hint, false, true);
        when(hintGenerationFacade.requestHint(submissionId, userId)).thenReturn(result);

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/hints", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stage").value(1));
    }

    @Test
    @DisplayName("이미 4단계까지 존재하면 새로 생성하지 않고 200을 반환한다")
    void requestHint_alreadyMaxStage_returns200() throws Exception {
        Hint hint = Hint.create(UUID.randomUUID(), 4, "4단계 힌트");
        HintGenerationFacade.HintGenerationResult result =
                new HintGenerationFacade.HintGenerationResult(UUID.randomUUID(), hint, true, false);
        when(hintGenerationFacade.requestHint(submissionId, userId)).thenReturn(result);

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/hints", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skipAvailable").value(true));
    }

    @Test
    @DisplayName("다른 사용자의 제출이면 404를 반환한다")
    void requestHint_otherUsersSubmission_returns404() throws Exception {
        when(hintGenerationFacade.requestHint(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/hints", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("오답 상태가 아닌 제출이면 403을 반환한다")
    void requestHint_notWrongStatus_returns403() throws Exception {
        when(hintGenerationFacade.requestHint(submissionId, userId))
                .thenThrow(new BusinessException(ErrorCode.HINT_NOT_ALLOWED));

        mockMvc.perform(post("/api/v1/coaching/submissions/{submissionId}/hints", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("HINT_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("힌트 목록 조회는 SuccessResponse 구조로 반환한다")
    void getHints_returnsSuccessResponseStructure() throws Exception {
        Hint hint = Hint.create(UUID.randomUUID(), 1, "1단계 힌트");
        when(hintQueryService.getHints(submissionId, userId)).thenReturn(List.of(hint));

        mockMvc.perform(get("/api/v1/coaching/submissions/{submissionId}/hints", submissionId)
                        .with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].stage").value(1))
                .andExpect(jsonPath("$.data[0].content").value("1단계 힌트"));
    }
}
