package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ChangePasswordRetryService;
import com.maesamco.user.application.service.GetMyGamificationService;
import com.maesamco.user.application.service.GetMyXpHistoriesService;
import com.maesamco.user.application.service.GetMyProfileService;
import com.maesamco.user.application.service.UpdateMyInterestsService;
import com.maesamco.user.application.service.UpdateMyProfileService;
import com.maesamco.user.application.service.WithdrawUserCommand;
import com.maesamco.user.application.service.WithdrawUserService;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.exception.GlobalExceptionHandler;
import com.maesamco.user.presentation.support.AuthCookieConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserApiController의 회원 탈퇴 HTTP 계약을 검증합니다.
 */
@WebMvcTest(
        value = UserApiController.class,
        properties = {
                "spring.jackson.deserialization."
                        + "fail-on-unknown-properties=true"
        }
)
@Import({
        AuthCookieConfig.class,
        GlobalExceptionHandler.class,
        UserApiControllerWithdrawTest
                .TestSecurityConfiguration.class
})
class UserApiControllerWithdrawTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final String CURRENT_PASSWORD =
            "Abcd1234!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMyGamificationService getMyGamificationService;

    @MockitoBean
    private GetMyXpHistoriesService getMyXpHistoriesService;

    @MockitoBean
    private WithdrawUserService withdrawUserService;

    @MockitoBean
    private GetMyProfileService getMyProfileService;

    @MockitoBean
    private ChangePasswordRetryService changePasswordRetryService;

    @MockitoBean
    private UpdateMyProfileService updateMyProfileService;

    @MockitoBean
    private UpdateMyInterestsService updateMyInterestsService;

    @Test
    @DisplayName(
            "인증된 사용자가 탈퇴하면 "
                    + "204와 만료된 Refresh Token Cookie를 반환한다"
    )
    void withdraw() throws Exception {
        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validRequest()
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

        verify(withdrawUserService)
                .withdraw(
                        USER_ID,
                        new WithdrawUserCommand(
                                CURRENT_PASSWORD
                        )
                );
    }

    @Test
    @DisplayName(
            "인증 정보 없이 탈퇴를 요청하면 "
                    + "401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void missingAuthentication() throws Exception {
        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validRequest()
                                )
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
                withdrawUserService
        );
    }

    @Test
    @DisplayName(
            "인증 principal이 UUID가 아니면 "
                    + "401 AUTH_INVALID_TOKEN을 반환한다"
    )
    void invalidAuthenticationPrincipal() throws Exception {
        // given
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        "invalid-user-id",
                        null,
                        List.of()
                );

        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(
                                        authentication(
                                                authenticationToken
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validRequest()
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

        verifyNoInteractions(
                withdrawUserService
        );
    }

    @Test
    @DisplayName(
            "현재 비밀번호가 누락되면 "
                    + "400 INVALID_INPUT_VALUE를 반환한다"
    )
    void missingCurrentPassword() throws Exception {
        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INVALID_INPUT_VALUE"
                                )
                )
                .andExpect(
                        jsonPath("$.error.fieldErrors[*].field")
                                .value(
                                        hasItem(
                                                "currentPassword"
                                        )
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verifyNoInteractions(
                withdrawUserService
        );
    }

    @Test
    @DisplayName(
            "현재 비밀번호가 너무 길면 400을 반환하고 "
                    + "비밀번호 원문을 노출하지 않는다"
    )
    void currentPasswordTooLong() throws Exception {
        // given
        String sensitivePassword =
                "A".repeat(65);

        String request = """
                {
                  "currentPassword": "%s"
                }
                """.formatted(
                sensitivePassword
        );

        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(request)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INVALID_INPUT_VALUE"
                                )
                )
                .andExpect(
                        jsonPath("$.error.fieldErrors[*].field")
                                .value(
                                        hasItem(
                                                "currentPassword"
                                        )
                                )
                )
                .andExpect(
                        content().string(
                                not(
                                        containsString(
                                                sensitivePassword
                                        )
                                )
                        )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verifyNoInteractions(
                withdrawUserService
        );
    }

    @Test
    @DisplayName(
            "Request Body에 userId를 전달하면 "
                    + "400 INVALID_INPUT_VALUE로 거부한다"
    )
    void protectedUserIdField() throws Exception {
        // given
        String request = """
                {
                  "userId": "22222222-2222-2222-2222-222222222222",
                  "currentPassword": "Abcd1234!"
                }
                """;

        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(request)
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

        verifyNoInteractions(
                withdrawUserService
        );
    }

    @Test
    @DisplayName(
            "현재 비밀번호가 일치하지 않으면 "
                    + "400 USER_CURRENT_PASSWORD_MISMATCH를 반환한다"
    )
    void currentPasswordMismatch() throws Exception {
        // given
        WithdrawUserCommand command =
                new WithdrawUserCommand(
                        CURRENT_PASSWORD
                );

        doThrow(
                new BusinessException(
                        ErrorCode.USER_CURRENT_PASSWORD_MISMATCH
                )
        )
                .when(withdrawUserService)
                .withdraw(
                        USER_ID,
                        command
                );

        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validRequest()
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "USER_CURRENT_PASSWORD_MISMATCH"
                                )
                )
                .andExpect(
                        content().string(
                                not(
                                        containsString(
                                                CURRENT_PASSWORD
                                        )
                                )
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
            "정지된 사용자가 탈퇴를 요청하면 "
                    + "403 USER_NOT_ACTIVE를 반환한다"
    )
    void inactiveUser() throws Exception {
        // given
        WithdrawUserCommand command =
                new WithdrawUserCommand(
                        CURRENT_PASSWORD
                );

        doThrow(
                new BusinessException(
                        ErrorCode.USER_NOT_ACTIVE
                )
        )
                .when(withdrawUserService)
                .withdraw(
                        USER_ID,
                        command
                );

        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validRequest()
                                )
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "USER_NOT_ACTIVE"
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
            "사용자를 찾을 수 없으면 "
                    + "404 USER_NOT_FOUND를 반환한다"
    )
    void userNotFound() throws Exception {
        // given
        WithdrawUserCommand command =
                new WithdrawUserCommand(
                        CURRENT_PASSWORD
                );

        doThrow(
                new BusinessException(
                        ErrorCode.USER_NOT_FOUND
                )
        )
                .when(withdrawUserService)
                .withdraw(
                        USER_ID,
                        command
                );

        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        validRequest()
                                )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "USER_NOT_FOUND"
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );
    }

    private UsernamePasswordAuthenticationToken createAuthentication(
            UUID userId
    ) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of()
        );
    }

    @Test
    @DisplayName(
            "소셜 회원이 Google ID Token으로 탈퇴를 요청하면 "
                    + "204를 반환하고 재인증 정보가 서비스로 전달된다 (#328)"
    )
    void withdrawWithGoogleIdToken() throws Exception {
        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "googleIdToken": "google-id-token"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isNoContent()
                )
                .andExpect(
                        header().string(
                                HttpHeaders.SET_COOKIE,
                                containsString(
                                        "Max-Age=0"
                                )
                        )
                );

        verify(withdrawUserService)
                .withdraw(
                        USER_ID,
                        new WithdrawUserCommand(
                                null,
                                "google-id-token"
                        )
                );
    }

    @Test
    @DisplayName(
            "비밀번호와 Google ID Token을 함께 보내면 "
                    + "400 INVALID_INPUT_VALUE를 반환한다 (#328)"
    )
    void bothCredentials() throws Exception {
        // when & then
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "currentPassword": "Abcd1234!",
                                          "googleIdToken": "google-id-token"
                                        }
                                        """
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
                )
                .andExpect(
                        jsonPath("$.error.fieldErrors[*].field")
                                .value(
                                        hasItem(
                                                "googleIdToken"
                                        )
                                )
                );

        verifyNoInteractions(
                withdrawUserService
        );
    }

    private String validRequest() {
        return """
                {
                  "currentPassword": "Abcd1234!"
                }
                """;
    }

    /**
     * 실제 애플리케이션과 동일하게 HTTP 계층에서는 요청을 통과시키고
     * Controller에서 인증 정보를 검증합니다.
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
