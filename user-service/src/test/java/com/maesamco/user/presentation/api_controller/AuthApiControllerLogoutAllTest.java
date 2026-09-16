package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.EmailVerificationService;
import com.maesamco.user.application.service.LoginService;
import com.maesamco.user.application.service.LogoutAllCommand;
import com.maesamco.user.application.service.LogoutAllService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthApiController의 전체 기기 로그아웃 HTTP 계약을 검증합니다.
 */
@WebMvcTest(AuthApiController.class)
@Import({
        GlobalExceptionHandler.class,
        AuthApiControllerLogoutAllTest
                .TestSecurityConfiguration.class
})
class AuthApiControllerLogoutAllTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private SignUpService signUpService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private RefreshService refreshService;

    @MockitoBean
    private LogoutService logoutService;

    @MockitoBean
    private LogoutAllService logoutAllService;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName(
            "인증된 사용자가 전체 로그아웃하면 모든 인증 세션을 종료하고 "
                    + "Refresh Token Cookie를 삭제한다"
    )
    void logoutAll() throws Exception {
        // given
        UsernamePasswordAuthenticationToken authenticationToken =
                createAuthentication();

        mockMvc.perform(
                        post("/api/v1/auth/logout-all")
                                .with(
                                        authentication(
                                                authenticationToken
                                        )
                                )
                )
                .andExpect(
                        status().isNoContent()
                )
                .andExpect(
                        header().string(
                                HttpHeaders.SET_COOKIE,
                                allOf(
                                        containsString(
                                                "refreshToken="
                                        ),
                                        containsString(
                                                "Path=/api/v1/auth"
                                        ),
                                        containsString(
                                                "Max-Age=0"
                                        ),
                                        containsString(
                                                "Secure"
                                        ),
                                        containsString(
                                                "HttpOnly"
                                        ),
                                        containsString(
                                                "SameSite=Lax"
                                        )
                                )
                        )
                )
                .andExpect(
                        content().string("")
                );

        verify(logoutAllService)
                .logoutAll(
                        new LogoutAllCommand(
                                USER_ID
                        )
                );
    }

    @Test
    @DisplayName(
            "인증 정보 없이 전체 로그아웃하면 "
                    + "401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void logoutAll_missingAuthentication()
            throws Exception {

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/logout-all")
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
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verifyNoInteractions(
                logoutAllService
        );
    }

    /**
     * 테스트용 인증 객체를 생성합니다.
     */
    private UsernamePasswordAuthenticationToken
    createAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                USER_ID,
                null,
                List.of()
        );
    }

    /**
     * 실제 애플리케이션과 동일하게 HTTP 계층에서는
     * 요청을 통과시키고 Controller에서 인증 정보를 검증합니다.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {

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
