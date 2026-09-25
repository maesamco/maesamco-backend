package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ChangePasswordRetryService;
import com.maesamco.user.application.service.GetMyGamificationResult;
import com.maesamco.user.application.service.GetMyGamificationService;
import com.maesamco.user.application.service.GetMyXpHistoriesService;
import com.maesamco.user.application.service.GetMyProfileService;
import com.maesamco.user.application.service.GetMyInterestsService;
import com.maesamco.user.application.service.UpdateMyInterestsService;
import com.maesamco.user.application.service.UpdateMyProfileService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserApiController.class)
@Import({
        AuthCookieConfig.class,
        GlobalExceptionHandler.class,
        UserApiControllerGetMyGamificationTest
                .TestSecurityConfiguration.class
})
class UserApiControllerGetMyGamificationTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

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
    private GetMyInterestsService getMyInterestsService;

    @MockitoBean
    private UpdateMyInterestsService updateMyInterestsService;

    @MockitoBean
    private WithdrawUserService withdrawUserService;

    @Test
    @DisplayName(
            "인증된 사용자가 조회하면 게이미피케이션 공개 필드만 반환한다"
    )
    void getMyGamification_returnsPublicState()
            throws Exception {

        when(
                getMyGamificationService
                        .getMyGamification(USER_ID)
        )
                .thenReturn(
                        new GetMyGamificationResult(
                                120L,
                                2,
                                3,
                                7,
                                LocalDate.of(
                                        2026,
                                        9,
                                        16
                                )
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/gamification"
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
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_JSON
                                )
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.totalXp")
                                .value(120)
                )
                .andExpect(
                        jsonPath("$.data.level")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.data.currentStreak")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.data.longestStreak")
                                .value(7)
                )
                .andExpect(
                        jsonPath("$.data.lastActivityDate")
                                .value(
                                        "2026-09-16"
                                )
                )
                .andExpect(
                        jsonPath("$.data.userId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.version")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.updatedAt")
                                .doesNotExist()
                );

        verify(
                getMyGamificationService
        )
                .getMyGamification(USER_ID);
    }

    @Test
    @DisplayName(
            "초기 상태는 lastActivityDate를 null로 반환한다"
    )
    void getMyGamification_returnsInitialState()
            throws Exception {

        when(
                getMyGamificationService
                        .getMyGamification(USER_ID)
        )
                .thenReturn(
                        new GetMyGamificationResult(
                                0L,
                                1,
                                0,
                                0,
                                null
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/gamification"
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
                        jsonPath(
                                "$.data.lastActivityDate"
                        )
                                .value(
                                        nullValue()
                                )
                );
    }

    @Test
    @DisplayName(
            "인증 정보가 없으면 401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void missingAuthentication()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/gamification"
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "AUTH_UNAUTHORIZED"
                                )
                );

        verifyNoInteractions(
                getMyGamificationService
        );
    }

    @Test
    @DisplayName(
            "UUID가 아닌 principal은 401 AUTH_INVALID_TOKEN을 반환한다"
    )
    void invalidPrincipal()
            throws Exception {

        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(
                        "invalid-user-id",
                        null,
                        List.of()
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/gamification"
                        )
                                .with(
                                        authentication(
                                                token
                                        )
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
                );

        verifyNoInteractions(
                getMyGamificationService
        );
    }

    @Test
    @DisplayName(
            "비활성 사용자는 403 USER_NOT_ACTIVE를 반환한다"
    )
    void inactiveUser()
            throws Exception {

        when(
                getMyGamificationService
                        .getMyGamification(USER_ID)
        )
                .thenThrow(
                        new BusinessException(
                                ErrorCode.USER_NOT_ACTIVE
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/gamification"
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
                        status().isForbidden()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "USER_NOT_ACTIVE"
                                )
                );
    }

    @Test
    @DisplayName(
            "사용자가 없으면 404 USER_NOT_FOUND를 반환한다"
    )
    void userNotFound()
            throws Exception {

        when(
                getMyGamificationService
                        .getMyGamification(USER_ID)
        )
                .thenThrow(
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/gamification"
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
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "USER_NOT_FOUND"
                                )
                );
    }

    @Test
    @DisplayName(
            "상태가 없으면 404 GAMIFICATION_STATE_NOT_FOUND를 반환한다"
    )
    void gamificationStateNotFound()
            throws Exception {

        when(
                getMyGamificationService
                        .getMyGamification(USER_ID)
        )
                .thenThrow(
                        new BusinessException(
                                ErrorCode.GAMIFICATION_STATE_NOT_FOUND
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/gamification"
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
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "GAMIFICATION_STATE_NOT_FOUND"
                                )
                )
                .andExpect(
                        jsonPath("$.error.message")
                                .value(
                                        "게이미피케이션 상태를 확인할 수 없습니다. "
                                                + "관리자에게 문의해주세요."
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
                    .authorizeHttpRequests(
                            auth ->
                                    auth.anyRequest()
                                            .permitAll()
                    );

            return http.build();
        }
    }
}
