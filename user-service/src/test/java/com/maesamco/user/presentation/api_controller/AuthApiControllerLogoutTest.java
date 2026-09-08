package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.LoginService;
import com.maesamco.user.application.service.LogoutCommand;
import com.maesamco.user.application.service.LogoutService;
import com.maesamco.user.application.service.RefreshService;
import com.maesamco.user.application.service.SignUpService;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.exception.GlobalExceptionHandler;
import com.maesamco.user.global.security.AccessTokenAuthenticationDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthApiController의 현재 인증 세션 로그아웃 HTTP 계약을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AuthApiControllerLogoutTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-07T00:00:00Z"
            );

    private static final Instant ACCESS_TOKEN_EXPIRES_AT =
            NOW.plusSeconds(900);

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    @Mock
    private SignUpService signUpService;

    @Mock
    private LoginService loginService;

    @Mock
    private RefreshService refreshService;

    @Mock
    private LogoutService logoutService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        AuthApiController authApiController =
                new AuthApiController(
                        signUpService,
                        loginService,
                        refreshService,
                        logoutService,
                        clock
                );

        JsonMapper jsonMapper =
                JsonMapper.builder()
                        .findAndAddModules()
                        .enable(
                                DeserializationFeature
                                        .FAIL_ON_UNKNOWN_PROPERTIES
                        )
                        .build();

        JacksonJsonHttpMessageConverter messageConverter =
                new JacksonJsonHttpMessageConverter(
                        jsonMapper
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(
                                authApiController
                        )
                        .setControllerAdvice(
                                new GlobalExceptionHandler()
                        )
                        .setMessageConverters(
                                messageConverter
                        )
                        .build();
    }

    @Test
    @DisplayName(
            "인증된 사용자가 로그아웃하면 현재 세션을 종료하고 "
                    + "Refresh Token Cookie를 삭제한다"
    )
    void logout() throws Exception {
        // given
        UsernamePasswordAuthenticationToken authentication =
                createAuthentication(
                        new AccessTokenAuthenticationDetails(
                                SESSION_ID,
                                ACCESS_TOKEN_EXPIRES_AT
                        )
                );

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .principal(authentication)
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
                )
                .andExpect(
                        content().string(
                                not(
                                        containsString(
                                                "access-token"
                                        )
                                )
                        )
                )
                .andExpect(
                        content().string(
                                not(
                                        containsString(
                                                "refresh-token"
                                        )
                                )
                        )
                );

        verify(logoutService)
                .logout(
                        new LogoutCommand(
                                USER_ID,
                                SESSION_ID,
                                ACCESS_TOKEN_EXPIRES_AT
                        )
                );

        verify(
                signUpService,
                never()
        ).signUp(any());

        verify(
                loginService,
                never()
        ).login(any());

        verify(
                refreshService,
                never()
        ).refresh(any());
    }

    @Test
    @DisplayName(
            "인증 정보 없이 로그아웃하면 "
                    + "401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void logout_missingAuthentication() throws Exception {
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
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verifyNoInteractions(logoutService);
    }

    @Test
    @DisplayName(
            "Access Token 인증 상세 정보가 없으면 "
                    + "401 AUTH_INVALID_TOKEN을 반환한다"
    )
    void logout_missingAuthenticationDetails()
            throws Exception {
        // given
        UsernamePasswordAuthenticationToken authentication =
                createAuthentication(null);

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .principal(authentication)
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
                                        "AUTH_INVALID_TOKEN"
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verifyNoInteractions(logoutService);
    }

    @Test
    @DisplayName(
            "만료된 Access Token으로 로그아웃하면 "
                    + "401 AUTH_EXPIRED_TOKEN을 반환하고 Cookie를 삭제하지 않는다"
    )
    void logout_expiredAccessToken() throws Exception {
        // given
        UsernamePasswordAuthenticationToken authentication =
                createAuthentication(
                        new AccessTokenAuthenticationDetails(
                                SESSION_ID,
                                NOW
                        )
                );

        doThrow(
                new BusinessException(
                        ErrorCode.AUTH_EXPIRED_TOKEN
                )
        )
                .when(logoutService)
                .logout(any());

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .principal(authentication)
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
                                        "AUTH_EXPIRED_TOKEN"
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verify(logoutService)
                .logout(
                        new LogoutCommand(
                                USER_ID,
                                SESSION_ID,
                                NOW
                        )
                );
    }

    @Test
    @DisplayName(
            "Redis 로그아웃 처리에 실패하면 "
                    + "성공 응답과 Cookie 삭제 헤더를 반환하지 않는다"
    )
    void logout_redisFailure() throws Exception {
        // given
        UsernamePasswordAuthenticationToken authentication =
                createAuthentication(
                        new AccessTokenAuthenticationDetails(
                                SESSION_ID,
                                ACCESS_TOKEN_EXPIRES_AT
                        )
                );

        doThrow(
                new IllegalStateException(
                        "Redis 로그아웃 처리 실패"
                )
        )
                .when(logoutService)
                .logout(any());

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .principal(authentication)
                )
                .andExpect(
                        status().isInternalServerError()
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verify(logoutService)
                .logout(
                        new LogoutCommand(
                                USER_ID,
                                SESSION_ID,
                                ACCESS_TOKEN_EXPIRES_AT
                        )
                );
    }

    /**
     * 테스트용 인증 객체를 생성합니다.
     */
    private UsernamePasswordAuthenticationToken
    createAuthentication(
            Object details
    ) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        USER_ID,
                        null,
                        List.of()
                );

        authentication.setDetails(details);

        return authentication;
    }
}
