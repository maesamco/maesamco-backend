package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.*;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserApiController의 내 정보 수정 HTTP 계약을 검증합니다.
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
        UserApiControllerUpdateMyProfileTest
                .TestSecurityConfiguration.class
})
class UserApiControllerUpdateMyProfileTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-15T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UpdateMyProfileService updateMyProfileService;

    @MockitoBean
    private GetMyProfileService getMyProfileService;

    @MockitoBean
    private ChangePasswordRetryService changePasswordRetryService;

    @Test
    @DisplayName(
            "인증된 사용자가 내 정보를 수정하면 "
                    + "200과 변경된 사용자 정보를 반환한다"
    )
    void updateMyProfile() throws Exception {
        // given
        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        "새닉네임",
                        LearningLevel.BASIC,
                        6
                );

        when(
                updateMyProfileService.updateMyProfile(
                        USER_ID,
                        command
                )
        ).thenReturn(
                createResult()
        );

        String request = """
                {
                  "nickname": "  새닉네임  ",
                  "learningLevel": "BASIC",
                  "javaExperienceMonths": 6
                }
                """;

        // when & then
        mockMvc.perform(
                        patch("/api/v1/users/me")
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
                        status().isOk()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.userId")
                                .value(USER_ID.toString())
                )
                .andExpect(
                        jsonPath("$.data.email")
                                .value("learner@example.com")
                )
                .andExpect(
                        jsonPath("$.data.nickname")
                                .value("새닉네임")
                )
                .andExpect(
                        jsonPath("$.data.role")
                                .value("USER")
                )
                .andExpect(
                        jsonPath("$.data.status")
                                .value("ACTIVE")
                )
                .andExpect(
                        jsonPath("$.data.learningLevel")
                                .value("BASIC")
                )
                .andExpect(
                        jsonPath("$.data.javaExperienceMonths")
                                .value(6)
                )
                .andExpect(
                        jsonPath("$.data.createdAt")
                                .value(CREATED_AT.toString())
                )
                .andExpect(
                        jsonPath("$.data.passwordHash")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.accessToken")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.refreshToken")
                                .doesNotExist()
                );

        verify(updateMyProfileService)
                .updateMyProfile(
                        USER_ID,
                        command
                );
    }

    @Test
    @DisplayName(
            "닉네임이 누락되면 400 INVALID_INPUT_VALUE를 반환한다"
    )
    void missingNickname() throws Exception {
        String request = """
                {
                  "learningLevel": "BASIC",
                  "javaExperienceMonths": 6
                }
                """;

        mockMvc.perform(
                        patch("/api/v1/users/me")
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
                                .value("INVALID_INPUT_VALUE")
                )
                .andExpect(
                        jsonPath("$.error.fieldErrors[*].field")
                                .value(
                                        hasItem("nickname")
                                )
                );

        verifyNoInteractions(
                updateMyProfileService
        );
    }

    @Test
    @DisplayName(
            "수정할 수 없는 필드를 전달하면 "
                    + "400 INVALID_INPUT_VALUE로 거부한다"
    )
    void protectedField() throws Exception {
        String request = """
                {
                  "nickname": "새닉네임",
                  "learningLevel": "BASIC",
                  "javaExperienceMonths": 6,
                  "role": "ADMIN"
                }
                """;

        mockMvc.perform(
                        patch("/api/v1/users/me")
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
                                .value("INVALID_INPUT_VALUE")
                );

        verifyNoInteractions(
                updateMyProfileService
        );
    }

    @Test
    @DisplayName(
            "인증 정보 없이 내 정보 수정을 요청하면 "
                    + "401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void missingAuthentication() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/users/me")
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
                                .value("AUTH_UNAUTHORIZED")
                );

        verifyNoInteractions(
                updateMyProfileService
        );
    }

    @Test
    @DisplayName(
            "인증 principal이 UUID가 아니면 "
                    + "401 AUTH_INVALID_TOKEN을 반환한다"
    )
    void invalidAuthenticationPrincipal() throws Exception {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        "invalid-user-id",
                        null,
                        List.of()
                );

        mockMvc.perform(
                        patch("/api/v1/users/me")
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
                                .value("AUTH_INVALID_TOKEN")
                );

        verifyNoInteractions(
                updateMyProfileService
        );
    }

    @Test
    @DisplayName(
            "닉네임이 중복되면 "
                    + "409 USER_DUPLICATE_NICKNAME을 반환한다"
    )
    void duplicateNickname() throws Exception {
        stubServiceFailure(
                ErrorCode.USER_DUPLICATE_NICKNAME
        );

        mockMvc.perform(
                        authenticatedRequest()
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "USER_DUPLICATE_NICKNAME"
                                )
                );
    }

    @Test
    @DisplayName(
            "활성 상태가 아닌 사용자는 "
                    + "403 USER_NOT_ACTIVE를 반환한다"
    )
    void inactiveUser() throws Exception {
        stubServiceFailure(
                ErrorCode.USER_NOT_ACTIVE
        );

        mockMvc.perform(
                        authenticatedRequest()
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("USER_NOT_ACTIVE")
                );
    }

    @Test
    @DisplayName(
            "사용자가 존재하지 않으면 "
                    + "404 USER_NOT_FOUND를 반환한다"
    )
    void userNotFound() throws Exception {
        stubServiceFailure(
                ErrorCode.USER_NOT_FOUND
        );

        mockMvc.perform(
                        authenticatedRequest()
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("USER_NOT_FOUND")
                );
    }

    private void stubServiceFailure(
            ErrorCode errorCode
    ) {
        when(
                updateMyProfileService.updateMyProfile(
                        USER_ID,
                        validCommand()
                )
        ).thenThrow(
                new BusinessException(errorCode)
        );
    }

    private org.springframework.test.web.servlet
            .request.MockHttpServletRequestBuilder
    authenticatedRequest() {
        return patch("/api/v1/users/me")
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
                );
    }

    private UpdateMyProfileCommand validCommand() {
        return new UpdateMyProfileCommand(
                "새닉네임",
                LearningLevel.BASIC,
                6
        );
    }

    private String validRequest() {
        return """
                {
                  "nickname": "새닉네임",
                  "learningLevel": "BASIC",
                  "javaExperienceMonths": 6
                }
                """;
    }

    private GetMyProfileResult createResult() {
        return new GetMyProfileResult(
                USER_ID,
                "learner@example.com",
                "새닉네임",
                UserRole.USER,
                UserStatus.ACTIVE,
                LearningLevel.BASIC,
                6,
                CREATED_AT
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
