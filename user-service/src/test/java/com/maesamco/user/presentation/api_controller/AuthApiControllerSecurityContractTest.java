package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.LoginService;
import com.maesamco.user.application.service.LogoutService;
import com.maesamco.user.application.service.RefreshService;
import com.maesamco.user.application.service.SignUpService;
import com.maesamco.user.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Spring Security 환경이 적용된 로그아웃 HTTP 계약을 검증합니다.
 */
@WebMvcTest(AuthApiController.class)
@Import({
        GlobalExceptionHandler.class,
        AuthApiControllerSecurityContractTest
                .MethodSecurityTestConfiguration.class
})
class AuthApiControllerSecurityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignUpService signUpService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private RefreshService refreshService;

    @MockitoBean
    private LogoutService logoutService;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName(
            "인증 없이 로그아웃하면 실제 SecurityFilterChain에서도 "
                    + "401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void logout_withoutAuthentication_returnsUnauthorized()
            throws Exception {
        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "AUTH_UNAUTHORIZED"
                                )
                );

        verifyNoInteractions(logoutService);
    }

    /**
     * 운영 설정과 동일하게 HTTP 요청은 통과시키고,
     * 메서드 단위 인증·인가만 활성화합니다.
     */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityTestConfiguration {

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http
        ) throws Exception {
            http
                    .csrf(
                            AbstractHttpConfigurer::disable
                    )
                    .httpBasic(
                            AbstractHttpConfigurer::disable
                    )
                    .formLogin(
                            AbstractHttpConfigurer::disable
                    )
                    .authorizeHttpRequests(auth ->
                            auth.anyRequest().permitAll()
                    );

            return http.build();
        }
    }
}
