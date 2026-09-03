package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.service.LoginService;
import com.maesamco.user.application.service.RefreshResult;
import com.maesamco.user.application.service.RefreshService;
import com.maesamco.user.application.service.SignUpService;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthApiController의 Refresh Token 재발급 HTTP 계약을 검증합니다.
 *
 * <p>Access Token은 응답 본문으로 전달하고,
 * 새 Refresh Token은 HttpOnly Cookie로만 전달하는지 검증합니다.</p>
 *
 * <p>Refresh Token Cookie 누락, 재사용 감지,
 * 비활성 사용자 등의 인증 실패 시 새로운 Cookie를
 * 발급하지 않는지도 함께 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthApiControllerRefreshTest {

    private static final Instant NOW =
            Instant.parse("2026-09-04T00:00:00Z");

    @Mock
    private SignUpService signUpService;

    @Mock
    private LoginService loginService;

    @Mock
    private RefreshService refreshService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                NOW,
                ZoneOffset.UTC
        );

        AuthApiController authApiController =
                new AuthApiController(
                        signUpService,
                        loginService,
                        refreshService,
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

        mockMvc = MockMvcBuilders
                .standaloneSetup(authApiController)
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
            "Refresh 성공 시 새 Access Token은 본문에, "
                    + "새 Refresh Token은 HttpOnly Cookie에 전달한다"
    )
    void refresh() throws Exception {
        // given
        Instant accessTokenExpiresAt =
                NOW.plusSeconds(900);

        Instant refreshTokenExpiresAt =
                NOW.plusSeconds(
                        60L * 60 * 24 * 7
                );

        IssuedTokens issuedTokens =
                new IssuedTokens(
                        "new-access-token",
                        accessTokenExpiresAt,
                        "new-refresh-token",
                        refreshTokenExpiresAt
                );

        RefreshResult result =
                new RefreshResult(
                        "new-access-token",
                        900,
                        issuedTokens
                );

        when(
                refreshService.refresh(any())
        ).thenReturn(result);

        MockCookie refreshCookie =
                new MockCookie(
                        "refreshToken",
                        "old-refresh-token"
                );

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(refreshCookie)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.accessToken")
                                .value("new-access-token")
                )
                .andExpect(
                        jsonPath(
                                "$.data.accessTokenExpiresIn"
                        ).value(900)
                )
                .andExpect(
                        jsonPath("$.data.issuedTokens")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.refreshToken")
                                .doesNotExist()
                )
                .andExpect(
                        content().string(
                                not(
                                        containsString(
                                                "new-refresh-token"
                                        )
                                )
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.SET_COOKIE,
                                allOf(
                                        containsString(
                                                "refreshToken=new-refresh-token"
                                        ),
                                        containsString(
                                                "Path=/api/v1/auth"
                                        ),
                                        containsString(
                                                "Max-Age=604800"
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
                );

        verify(refreshService)
                .refresh(any());
    }

    @Test
    @DisplayName(
            "Refresh Token Cookie가 없으면 "
                    + "401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void refresh_missingCookie() throws Exception {
        // given
        when(
                refreshService.refresh(any())
        ).thenThrow(
                new BusinessException(
                        ErrorCode.AUTH_UNAUTHORIZED
                )
        );

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
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
                        jsonPath("$.error.message")
                                .value(
                                        "인증이 필요합니다."
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verify(refreshService)
                .refresh(any());
    }

    @Test
    @DisplayName(
            "이전 Refresh Token 재사용이 감지되면 "
                    + "401 AUTH_REFRESH_TOKEN_REUSED를 반환한다"
    )
    void refresh_reusedToken() throws Exception {
        // given
        when(
                refreshService.refresh(any())
        ).thenThrow(
                new BusinessException(
                        ErrorCode.AUTH_REFRESH_TOKEN_REUSED
                )
        );

        MockCookie refreshCookie =
                new MockCookie(
                        "refreshToken",
                        "already-used-refresh-token"
                );

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(refreshCookie)
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
                                        "AUTH_REFRESH_TOKEN_REUSED"
                                )
                )
                .andExpect(
                        jsonPath("$.error.message")
                                .value(
                                        "Refresh Token 재사용이 감지되었습니다. 다시 로그인해주세요."
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verify(refreshService)
                .refresh(any());
    }

    @Test
    @DisplayName(
            "정지된 사용자가 Refresh를 요청하면 "
                    + "403 USER_NOT_ACTIVE를 반환한다"
    )
    void refresh_userNotActive() throws Exception {
        // given
        when(
                refreshService.refresh(any())
        ).thenThrow(
                new BusinessException(
                        ErrorCode.USER_NOT_ACTIVE
                )
        );

        MockCookie refreshCookie =
                new MockCookie(
                        "refreshToken",
                        "refresh-token"
                );

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(refreshCookie)
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "USER_NOT_ACTIVE"
                                )
                )
                .andExpect(
                        jsonPath("$.error.message")
                                .value(
                                        "현재 로그인할 수 없는 계정입니다."
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verify(refreshService)
                .refresh(any());
    }

    @Test
    @DisplayName("Refresh API는 회원가입과 로그인 서비스를 호출하지 않는다")
    void refresh_doesNotCallOtherAuthServices() throws Exception {
        // given
        IssuedTokens issuedTokens =
                new IssuedTokens(
                        "new-access-token",
                        NOW.plusSeconds(900),
                        "new-refresh-token",
                        NOW.plusSeconds(3600)
                );

        when(
                refreshService.refresh(any())
        ).thenReturn(
                new RefreshResult(
                        "new-access-token",
                        900,
                        issuedTokens
                )
        );

        MockCookie refreshCookie =
                new MockCookie(
                        "refreshToken",
                        "old-refresh-token"
                );

        // when
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(refreshCookie)
                )
                .andExpect(
                        status().isOk()
                );

        // then
        verify(
                signUpService,
                never()
        ).signUp(any());

        verify(
                loginService,
                never()
        ).login(any());
    }
}
