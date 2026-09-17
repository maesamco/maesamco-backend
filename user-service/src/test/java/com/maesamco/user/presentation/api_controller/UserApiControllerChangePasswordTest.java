package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.*;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.exception.GlobalExceptionHandler;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserApiController의 비밀번호 변경 HTTP 계약을 검증합니다.
 */
@WebMvcTest(
        value = UserApiController.class,
        properties = {
                "spring.jackson.deserialization."
                        + "fail-on-unknown-properties=true"
        }
)
@Import({
        GlobalExceptionHandler.class,
        UserApiControllerChangePasswordTest
                .TestSecurityConfiguration.class
})
class UserApiControllerChangePasswordTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final String CURRENT_PASSWORD =
            "Abcd1234!";

    private static final String NEW_PASSWORD =
            "NewAbcd1234!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMyGamificationService getMyGamificationService;

    @MockitoBean
    private ChangePasswordRetryService changePasswordRetryService;

    @MockitoBean
    private GetMyProfileService getMyProfileService;

    @MockitoBean
    private UpdateMyProfileService updateMyProfileService;

    @MockitoBean
    private UpdateMyInterestsService updateMyInterestsService;

    @MockitoBean
    private WithdrawUserService withdrawUserService;

    @Test
    @DisplayName(
            "인증된 사용자가 비밀번호를 변경하면 "
                    + "204와 만료된 Refresh Token Cookie를 반환한다"
    )
    void changePassword() throws Exception {
        // given
        UsernamePasswordAuthenticationToken authenticationToken =
                createAuthentication(
                        USER_ID
                );

        // when & then
        mockMvc.perform(
                        patch("/api/v1/users/me/password")
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

        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        verify(changePasswordRetryService)
                .changePassword(
                        USER_ID,
                        command
                );
    }

    @Test
    @DisplayName(
            "인증 정보 없이 비밀번호 변경을 요청하면 "
                    + "401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void missingAuthentication() throws Exception {
        // when & then
        mockMvc.perform(
                        patch("/api/v1/users/me/password")
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
                changePasswordRetryService
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
                        patch("/api/v1/users/me/password")
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
                changePasswordRetryService
        );
    }

    @Test
    @DisplayName(
            "현재 비밀번호가 누락되면 "
                    + "400 INVALID_INPUT_VALUE를 반환한다"
    )
    void missingCurrentPassword() throws Exception {
        // given
        String request = """
                {
                  "newPassword": "NewAbcd1234!"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch("/api/v1/users/me/password")
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
                                                NEW_PASSWORD
                                        )
                                )
                        )
                );

        verifyNoInteractions(
                changePasswordRetryService
        );
    }

    @Test
    @DisplayName(
            "새 비밀번호가 정책을 위반하면 "
                    + "400을 반환하고 비밀번호 원문을 노출하지 않는다"
    )
    void invalidNewPassword() throws Exception {
        // given
        String invalidNewPassword =
                "weak-password";

        String request = """
                {
                  "currentPassword": "Abcd1234!",
                  "newPassword": "weak-password"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch("/api/v1/users/me/password")
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
                                                "newPassword"
                                        )
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
                        content().string(
                                not(
                                        containsString(
                                                invalidNewPassword
                                        )
                                )
                        )
                );

        verifyNoInteractions(
                changePasswordRetryService
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
                  "currentPassword": "Abcd1234!",
                  "newPassword": "NewAbcd1234!"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch("/api/v1/users/me/password")
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
                changePasswordRetryService
        );
    }

    @Test
    @DisplayName(
            "현재 비밀번호가 일치하지 않으면 "
                    + "400 USER_CURRENT_PASSWORD_MISMATCH를 반환한다"
    )
    void currentPasswordMismatch() throws Exception {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        doThrow(
                new BusinessException(
                        ErrorCode.USER_CURRENT_PASSWORD_MISMATCH
                )
        )
                .when(changePasswordRetryService)
                .changePassword(
                        USER_ID,
                        command
                );

        // when & then
        mockMvc.perform(
                        patch("/api/v1/users/me/password")
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
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );
    }

    @Test
    @DisplayName(
            "새 비밀번호가 서비스 정책을 위반하면 "
                    + "400 USER_PASSWORD_POLICY_VIOLATION을 반환한다"
    )
    void passwordPolicyViolation() throws Exception {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        doThrow(
                new BusinessException(
                        ErrorCode.USER_PASSWORD_POLICY_VIOLATION
                )
        )
                .when(changePasswordRetryService)
                .changePassword(
                        USER_ID,
                        command
                );

        // when & then
        mockMvc.perform(
                        patch("/api/v1/users/me/password")
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
                                        "USER_PASSWORD_POLICY_VIOLATION"
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

    private String validRequest() {
        return """
                {
                  "currentPassword": "Abcd1234!",
                  "newPassword": "NewAbcd1234!"
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

    @Test
    @DisplayName(
            "비밀번호 변경 중 동시 수정 충돌이 지속되면 "
                    + "409 USER_PASSWORD_CHANGE_CONFLICT를 반환한다"
    )
    void passwordChangeConflict() throws Exception {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        doThrow(
                new BusinessException(
                        ErrorCode.USER_PASSWORD_CHANGE_CONFLICT
                )
        )
                .when(changePasswordRetryService)
                .changePassword(
                        USER_ID,
                        command
                );

        // when & then
        mockMvc.perform(
                        patch("/api/v1/users/me/password")
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
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "USER_PASSWORD_CHANGE_CONFLICT"
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );
    }
}
