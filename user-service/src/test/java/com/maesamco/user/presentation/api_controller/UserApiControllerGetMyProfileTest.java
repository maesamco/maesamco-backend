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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserApiController의 내 정보 조회 HTTP 계약을 검증합니다.
 */
@WebMvcTest(UserApiController.class)
@Import({
        GlobalExceptionHandler.class,
        UserApiControllerGetMyProfileTest
                .TestSecurityConfiguration.class
})
class UserApiControllerGetMyProfileTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID OTHER_USER_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-15T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMyGamificationService getMyGamificationService;

    @MockitoBean
    private GetMyXpHistoriesService getMyXpHistoriesService;

    @MockitoBean
    private GetMyProfileService getMyProfileService;

    @MockitoBean
    private ChangePasswordRetryService changePasswordRetryService;

    @MockitoBean
    private UpdateMyProfileService updateMyProfileService;

    @MockitoBean
    private UpdateMyInterestsService updateMyInterestsService;

    @MockitoBean
    private WithdrawUserService withdrawUserService;

    @Test
    @DisplayName(
            "인증된 사용자가 내 정보를 조회하면 "
                    + "200과 사용자 기본 정보를 반환한다"
    )
    void getMyProfile() throws Exception {
        // given
        GetMyProfileResult result =
                createResult();

        when(getMyProfileService.getMyProfile(USER_ID))
                .thenReturn(result);

        // when & then
        mockMvc.perform(
                        get("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
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
                                .value("김티암")
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
                                .value("BEGINNER")
                )
                .andExpect(
                        jsonPath("$.data.javaExperienceMonths")
                                .value(3)
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

        verify(getMyProfileService)
                .getMyProfile(USER_ID);
    }

    @Test
    @DisplayName(
            "Query Parameter의 userId는 인증된 사용자 식별자를 "
                    + "변경할 수 없다"
    )
    void userIdParameterCannotOverrideAuthentication()
            throws Exception {
        // given
        when(getMyProfileService.getMyProfile(USER_ID))
                .thenReturn(createResult());

        // when & then
        mockMvc.perform(
                        get("/api/v1/users/me")
                                .param(
                                        "userId",
                                        OTHER_USER_ID.toString()
                                )
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.data.userId")
                                .value(USER_ID.toString())
                );

        verify(getMyProfileService)
                .getMyProfile(USER_ID);
    }

    @Test
    @DisplayName(
            "인증 정보 없이 내 정보를 조회하면 "
                    + "401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void missingAuthentication() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users/me")
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
                                .value("AUTH_UNAUTHORIZED")
                );

        verifyNoInteractions(
                getMyProfileService
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
                        get("/api/v1/users/me")
                                .with(
                                        authentication(
                                                authenticationToken
                                        )
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
                getMyProfileService
        );
    }

    @Test
    @DisplayName(
            "사용자가 존재하지 않으면 "
                    + "404 USER_NOT_FOUND를 반환한다"
    )
    void userNotFound() throws Exception {
        // given
        when(getMyProfileService.getMyProfile(USER_ID))
                .thenThrow(
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        // when & then
        mockMvc.perform(
                        get("/api/v1/users/me")
                                .with(
                                        authentication(
                                                createAuthentication(
                                                        USER_ID
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("USER_NOT_FOUND")
                );
    }

    private GetMyProfileResult createResult() {
        return new GetMyProfileResult(
                USER_ID,
                "learner@example.com",
                "김티암",
                UserRole.USER,
                UserStatus.ACTIVE,
                LearningLevel.BEGINNER,
                3,
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
