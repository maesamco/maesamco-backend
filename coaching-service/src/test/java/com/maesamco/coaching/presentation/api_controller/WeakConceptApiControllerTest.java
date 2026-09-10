package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.query_service.WeakConceptQueryService;
import com.maesamco.coaching.domain.entity.WeakConcept;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HintApiControllerTest와 동일한 이유 — Facade/QueryService 단위 테스트만으로는 Security
 * 설정 변경을 못 잡으므로 Controller 레벨 HTTP 계약을 별도로 검증한다.
 */
@WebMvcTest(WeakConceptApiController.class)
@Import(WeakConceptApiControllerTest.TestSecurityConfig.class)
class WeakConceptApiControllerTest {

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

    private final UUID userId = UUID.randomUUID();

    private static RequestPostProcessor asUser(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    @Test
    @DisplayName("인증되지 않은 요청은 401을 반환한다")
    void getWeakConcepts_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/coaching/weak-concepts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("본인의 취약 개념 목록을 SuccessResponse 구조로 반환한다")
    void getWeakConcepts_returnsSuccessResponseStructure() throws Exception {
        WeakConcept weakConcept = WeakConcept.create(userId, "경계값 처리");
        when(weakConceptQueryService.getWeakConcepts(userId)).thenReturn(List.of(weakConcept));

        mockMvc.perform(get("/api/v1/coaching/weak-concepts").with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].conceptTag").value("경계값 처리"))
                .andExpect(jsonPath("$.data[0].occurrenceCount").value(1))
                .andExpect(jsonPath("$.data[0].improved").value(false));
    }

    @Test
    @DisplayName("취약 개념이 없으면 빈 배열을 200으로 반환한다")
    void getWeakConcepts_returnsEmptyArray_whenNone() throws Exception {
        when(weakConceptQueryService.getWeakConcepts(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/coaching/weak-concepts").with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
