package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.service.SocialLoginResult;
import com.maesamco.user.application.service.SocialLoginService;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.exception.GlobalExceptionHandler;
import com.maesamco.user.presentation.support.AuthCookieProperties;
import com.maesamco.user.presentation.support.RefreshTokenCookieFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
 * Google 소셜 로그인 HTTP 계약을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class SocialAuthApiControllerTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-23T13:00:00Z"
            );

    @Mock
    private SocialLoginService socialLoginService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        RefreshTokenCookieFactory
                refreshTokenCookieFactory =
                new RefreshTokenCookieFactory(
                        new AuthCookieProperties(
                                true,
                                "Lax",
                                "/api/v1/auth"
                        )
                );

        SocialAuthApiController controller =
                new SocialAuthApiController(
                        socialLoginService,
                        refreshTokenCookieFactory,
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

        JacksonJsonHttpMessageConverter
                messageConverter =
                new JacksonJsonHttpMessageConverter(
                        jsonMapper
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(
                                controller
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
            "기존 Google 소셜 회원 로그인 성공 시 Access Token과 Refresh Cookie를 반환한다"
    )
    void googleLogin_authenticated()
            throws Exception {
        // given
        User user =
                User.create(
                        "encrypted-email",
                        "a".repeat(64),
                        "argon2-password-hash",
                        "김티암",
                        3,
                        LearningLevel.BEGINNER
                );

        IssuedTokens issuedTokens =
                new IssuedTokens(
                        "access-token",
                        NOW.plusSeconds(900),
                        "refresh-token",
                        NOW.plusSeconds(
                                60L * 60 * 24 * 14
                        )
                );

        SocialLoginResult result =
                SocialLoginResult.authenticated(
                        SocialProvider.GOOGLE,
                        user,
                        issuedTokens,
                        900
                );

        when(
                socialLoginService.login(any())
        ).thenReturn(result);

        String requestBody =
                """
                {
                  "idToken": "google-id-token"
                }
                """;

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/auth/social/google"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.status")
                                .value("AUTHENTICATED")
                )
                .andExpect(
                        jsonPath("$.data.provider")
                                .value("GOOGLE")
                )
                .andExpect(
                        jsonPath("$.data.userId")
                                .value(
                                        user.getId()
                                                .toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.accessToken")
                                .value(
                                        "access-token"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.data.accessTokenExpiresIn"
                        )
                                .value(900)
                )
                .andExpect(
                        jsonPath(
                                "$.data.issuedTokens"
                        )
                                .doesNotExist()
                )
                .andExpect(
                        header().string(
                                HttpHeaders.SET_COOKIE,
                                containsString(
                                        "refreshToken=refresh-token"
                                )
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.SET_COOKIE,
                                containsString(
                                        "HttpOnly"
                                )
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.SET_COOKIE,
                                containsString(
                                        "Secure"
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

        verify(socialLoginService)
                .login(any());
    }

    @Test
    @DisplayName(
            "신규 Google 사용자는 SIGNUP_REQUIRED를 반환하고 인증 Cookie를 발급하지 않는다"
    )
    void googleLogin_signupRequired()
            throws Exception {
        // given
        SocialLoginResult result =
                SocialLoginResult.signupRequired(
                        SocialProvider.GOOGLE,
                        "social-signup-token",
                        "learner@example.com"
                );

        when(
                socialLoginService.login(any())
        ).thenReturn(result);

        String requestBody =
                """
                {
                  "idToken": "google-id-token"
                }
                """;

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/auth/social/google"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.status")
                                .value(
                                        "SIGNUP_REQUIRED"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.data.socialSignupToken"
                        )
                                .value(
                                        "social-signup-token"
                                )
                )
                .andExpect(
                        jsonPath("$.data.email")
                                .value(
                                        "learner@example.com"
                                )
                )
                .andExpect(
                        jsonPath("$.data.accessToken")
                                .doesNotExist()
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verify(socialLoginService)
                .login(any());
    }

    @Test
    @DisplayName(
            "일반 회원가입 이메일과 충돌하면 409를 반환하고 Cookie를 발급하지 않는다"
    )
    void googleLogin_existingLocalEmail()
            throws Exception {
        // given
        when(
                socialLoginService.login(any())
        ).thenThrow(
                new BusinessException(
                        ErrorCode
                                .SOCIAL_SIGNUP_EMAIL_ALREADY_EXISTS
                )
        );

        String requestBody =
                """
                {
                  "idToken": "google-id-token"
                }
                """;

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/auth/social/google"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "SOCIAL_SIGNUP_EMAIL_ALREADY_EXISTS"
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );
    }

    @Test
    @DisplayName(
            "유효하지 않은 Google ID Token이면 401을 반환한다"
    )
    void googleLogin_invalidToken()
            throws Exception {
        // given
        when(
                socialLoginService.login(any())
        ).thenThrow(
                new BusinessException(
                        ErrorCode.AUTH_INVALID_TOKEN
                )
        );

        String requestBody =
                """
                {
                  "idToken": "invalid-google-id-token"
                }
                """;

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/auth/social/google"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isUnauthorized()
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
    }

    @Test
    @DisplayName(
            "Google ID Token이 비어 있으면 400을 반환하고 Service를 호출하지 않는다"
    )
    void googleLogin_blankToken()
            throws Exception {
        // given
        String requestBody =
                """
                {
                  "idToken": ""
                }
                """;

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/auth/social/google"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INVALID_INPUT_VALUE"
                                )
                );

        verify(
                socialLoginService,
                never()
        ).login(any());
    }

    @Test
    @DisplayName(
            "Google 로그인 요청에 정의되지 않은 필드가 있으면 400을 반환한다"
    )
    void googleLogin_rejectsUnknownField()
            throws Exception {
        // given
        String requestBody =
                """
                {
                  "idToken": "google-id-token",
                  "email": "attacker@example.com"
                }
                """;

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/auth/social/google"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );

        verify(
                socialLoginService,
                never()
        ).login(any());
    }
}
