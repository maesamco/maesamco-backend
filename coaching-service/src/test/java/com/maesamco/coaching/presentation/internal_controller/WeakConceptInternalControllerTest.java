package com.maesamco.coaching.presentation.internal_controller;

import com.maesamco.coaching.application.query_service.WeakConceptQueryService;
import com.maesamco.coaching.domain.entity.WeakConcept;
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
 * 이 슬라이스 테스트는 컨트롤러 로직만 검증한다 — 실제 HmacVerificationFilter(HmacFilterConfig,
 * FilterRegistrationBean으로 등록)는 @WebMvcTest 스캔 대상이 아니라서 여기서는 걸리지 않는다.
 * HMAC 서명 검증 자체는 HmacVerificationFilter가 이미 별도로 책임지는 부분이라(모든
 * /internal/v1/** 경로에 공통 적용), 이 컨트롤러 전용 테스트에서 다시 검증하지 않는다.
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

        mockMvc.perform(get("/internal/v1/users/{userId}/weak-concepts", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].conceptTag").value("경계값 처리"));
    }

    @Test
    @DisplayName("취약 개념이 없는 사용자는 빈 배열을 200으로 반환한다")
    void getWeakConcepts_returnsEmptyArray_whenNone() throws Exception {
        UUID userId = UUID.randomUUID();
        when(weakConceptQueryService.getWeakConcepts(userId)).thenReturn(List.of());

        mockMvc.perform(get("/internal/v1/users/{userId}/weak-concepts", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("userId 형식이 잘못되면 400(INVALID_INPUT_VALUE)을 반환한다")
    void getWeakConcepts_invalidUserId_returns400() throws Exception {
        mockMvc.perform(get("/internal/v1/users/{userId}/weak-concepts", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }
}
