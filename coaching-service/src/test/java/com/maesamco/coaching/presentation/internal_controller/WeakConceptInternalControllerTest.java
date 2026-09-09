package com.maesamco.coaching.presentation.internal_controller;

import com.maesamco.coaching.application.query_service.WeakConceptQueryService;
import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.global.security.hmac.InternalCallHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이 슬라이스 테스트는 컨트롤러 로직(성공 응답 구조, 호출자 서비스 인가, 경로변수 검증)만
 * 검증한다 — 실제 HmacVerificationFilter(HmacFilterConfig, FilterRegistrationBean으로
 * 등록)는 @WebMvcTest 스캔 대상이 아니라서 여기서는 걸리지 않는다. HMAC 서명 검증 자체는
 * {@link WeakConceptInternalControllerSecurityTest}가 실제 필터 체인으로 별도 검증한다
 * (PR #124 심층 재검토, 용현님 — 이 슬라이스 테스트만으로는 HMAC 필터를 검증할 수 없다는
 * 지적 반영).
 *
 * 프로덕션에서는 `X-Internal-Service` 헤더가 이미 HmacVerificationFilter를 통과한
 * 요청에만 존재하지만, 여기서는 필터가 없으므로 컨트롤러의 호출자 인가 로직을 검증하려면
 * 테스트가 직접 헤더를 채워 보내야 한다.
 */
@WebMvcTest(WeakConceptInternalController.class)
@Import(WeakConceptInternalControllerTest.TestSecurityConfig.class)
class WeakConceptInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeakConceptQueryService weakConceptQueryService;

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Test
    @DisplayName("경로의 userId로 취약 개념 목록을 SuccessResponse 구조로 반환한다")
    void getWeakConcepts_returnsSuccessResponseStructure() throws Exception {
        UUID userId = UUID.randomUUID();
        WeakConcept weakConcept = WeakConcept.create(userId, "경계값 처리");
        when(weakConceptQueryService.getWeakConcepts(userId)).thenReturn(List.of(weakConcept));

        mockMvc.perform(get("/internal/v1/users/{userId}/weak-concepts", userId)
                        .header(InternalCallHeaders.SERVICE, "content-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].conceptTag").value("경계값 처리"));
    }

    @Test
    @DisplayName("취약 개념이 없는 사용자는 빈 배열을 200으로 반환한다")
    void getWeakConcepts_returnsEmptyArray_whenNone() throws Exception {
        UUID userId = UUID.randomUUID();
        when(weakConceptQueryService.getWeakConcepts(userId)).thenReturn(List.of());

        mockMvc.perform(get("/internal/v1/users/{userId}/weak-concepts", userId)
                        .header(InternalCallHeaders.SERVICE, "content-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("userId 형식이 잘못되면 400(INVALID_INPUT_VALUE)을 반환한다")
    void getWeakConcepts_invalidUserId_returns400() throws Exception {
        mockMvc.perform(get("/internal/v1/users/{userId}/weak-concepts", "not-a-uuid")
                        .header(InternalCallHeaders.SERVICE, "content-service"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("Content Service가 아닌 호출자는 403(INTERNAL_CALLER_NOT_ALLOWED)을 반환한다")
    void getWeakConcepts_disallowedCaller_returns403() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/internal/v1/users/{userId}/weak-concepts", userId)
                        .header(InternalCallHeaders.SERVICE, "some-other-service"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_CALLER_NOT_ALLOWED"));
    }
}
