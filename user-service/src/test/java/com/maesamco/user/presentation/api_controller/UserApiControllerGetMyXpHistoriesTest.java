package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ChangePasswordRetryService;
import com.maesamco.user.application.service.GetMyGamificationService;
import com.maesamco.user.application.service.GetMyProfileService;
import com.maesamco.user.application.service.GetMyXpHistoriesQuery;
import com.maesamco.user.application.service.GetMyXpHistoriesResult;
import com.maesamco.user.application.service.GetMyXpHistoriesService;
import com.maesamco.user.application.service.GetMyXpHistoryItemResult;
import com.maesamco.user.application.service.GetMyInterestsService;
import com.maesamco.user.application.service.UpdateMyInterestsService;
import com.maesamco.user.application.service.UpdateMyProfileService;
import com.maesamco.user.application.service.WithdrawUserService;
import com.maesamco.user.domain.entity.RewardType;
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

import java.time.Instant;
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

/**
 * 내 XP 이력 조회 API의 HTTP 계약을 검증합니다.
 */
@WebMvcTest(UserApiController.class)
@Import({
        AuthCookieConfig.class,
        GlobalExceptionHandler.class,
        UserApiControllerGetMyXpHistoriesTest
                .TestSecurityConfiguration.class
})
class UserApiControllerGetMyXpHistoriesTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMyXpHistoriesService getMyXpHistoriesService;

    @MockitoBean
    private GetMyGamificationService getMyGamificationService;

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
            "size와 cursor를 생략하면 기본 크기 20으로 XP 이력을 조회한다"
    )
    void getMyXpHistories_usesDefaultSize() throws Exception {
        GetMyXpHistoriesQuery expectedQuery =
                new GetMyXpHistoriesQuery(
                        20,
                        null
                );

        when(
                getMyXpHistoriesService
                        .getMyXpHistories(
                                USER_ID,
                                expectedQuery
                        )
        )
                .thenReturn(
                        new GetMyXpHistoriesResult(
                                List.of(
                                        new GetMyXpHistoryItemResult(
                                                RewardType.FIRST_CORRECT,
                                                10,
                                                120L,
                                                "문제 최초 정답 보상",
                                                Instant.parse(
                                                        "2026-09-17T01:20:30Z"
                                                )
                                        )
                                ),
                                "next-cursor",
                                true
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
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
                        jsonPath("$.data.items.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.items[0].rewardType")
                                .value("FIRST_CORRECT")
                )
                .andExpect(
                        jsonPath("$.data.items[0].amount")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.data.items[0].balanceAfter")
                                .value(120)
                )
                .andExpect(
                        jsonPath("$.data.items[0].description")
                                .value("문제 최초 정답 보상")
                )
                .andExpect(
                        jsonPath("$.data.items[0].earnedAt")
                                .value(
                                        "2026-09-17T01:20:30Z"
                                )
                )
                .andExpect(
                        jsonPath("$.data.nextCursor")
                                .value("next-cursor")
                )
                .andExpect(
                        jsonPath("$.data.hasNext")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.items[0].id")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.items[0].userId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.items[0].sourceEventId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.items[0].sourceType")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.items[0].sourceId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.items[0].problemId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.items[0].rewardDate")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.items[0].createdAt")
                                .doesNotExist()
                );

        verify(
                getMyXpHistoriesService
        )
                .getMyXpHistories(
                        USER_ID,
                        expectedQuery
                );
    }

    @Test
    @DisplayName(
            "요청한 size와 cursor를 조회 Service에 전달한다"
    )
    void getMyXpHistories_passesExplicitParameters() throws Exception {
        GetMyXpHistoriesQuery expectedQuery =
                new GetMyXpHistoriesQuery(
                        50,
                        "opaque-cursor"
                );

        when(
                getMyXpHistoriesService
                        .getMyXpHistories(
                                USER_ID,
                                expectedQuery
                        )
        )
                .thenReturn(
                        new GetMyXpHistoriesResult(
                                List.of(),
                                null,
                                false
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
                        )
                                .param(
                                        "size",
                                        "50"
                                )
                                .param(
                                        "cursor",
                                        "opaque-cursor"
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
                );

        verify(
                getMyXpHistoriesService
        )
                .getMyXpHistories(
                        USER_ID,
                        expectedQuery
                );
    }

    @Test
    @DisplayName(
            "XP 이력이 없으면 빈 목록과 null cursor를 반환한다"
    )
    void getMyXpHistories_returnsEmptyPage() throws Exception {
        when(
                getMyXpHistoriesService
                        .getMyXpHistories(
                                USER_ID,
                                new GetMyXpHistoriesQuery(
                                        20,
                                        null
                                )
                        )
        )
                .thenReturn(
                        new GetMyXpHistoriesResult(
                                List.of(),
                                null,
                                false
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
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
                        jsonPath("$.data.items")
                                .isEmpty()
                )
                .andExpect(
                        jsonPath("$.data.nextCursor")
                                .value(
                                        nullValue()
                                )
                )
                .andExpect(
                        jsonPath("$.data.hasNext")
                                .value(false)
                );
    }

    @Test
    @DisplayName(
            "size가 0이면 400 INVALID_INPUT_VALUE를 반환한다"
    )
    void getMyXpHistories_rejectsSizeBelowMinimum() throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
                        )
                                .param(
                                        "size",
                                        "0"
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
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INVALID_INPUT_VALUE"
                                )
                );

        verifyNoInteractions(
                getMyXpHistoriesService
        );
    }

    @Test
    @DisplayName(
            "size가 101이면 400 INVALID_INPUT_VALUE를 반환한다"
    )
    void getMyXpHistories_rejectsSizeAboveMaximum() throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
                        )
                                .param(
                                        "size",
                                        "101"
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
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INVALID_INPUT_VALUE"
                                )
                );

        verifyNoInteractions(
                getMyXpHistoriesService
        );
    }

    @Test
    @DisplayName(
            "size가 숫자가 아니면 400 INVALID_INPUT_VALUE를 반환한다"
    )
    void getMyXpHistories_rejectsNonNumericSize() throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
                        )
                                .param(
                                        "size",
                                        "not-a-number"
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
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INVALID_INPUT_VALUE"
                                )
                );

        verifyNoInteractions(
                getMyXpHistoriesService
        );
    }

    @Test
    @DisplayName(
            "빈 cursor는 400 INVALID_INPUT_VALUE를 반환한다"
    )
    void getMyXpHistories_rejectsEmptyCursor() throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
                        )
                                .param(
                                        "cursor",
                                        ""
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
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INVALID_INPUT_VALUE"
                                )
                );

        verifyNoInteractions(
                getMyXpHistoriesService
        );
    }

    @Test
    @DisplayName(
            "인증 정보가 없으면 401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void getMyXpHistories_rejectsMissingAuthentication()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
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
                getMyXpHistoriesService
        );
    }

    @Test
    @DisplayName(
            "UUID가 아닌 principal은 401 AUTH_INVALID_TOKEN을 반환한다"
    )
    void getMyXpHistories_rejectsInvalidPrincipal()
            throws Exception {

        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(
                        "invalid-user-id",
                        null,
                        List.of()
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
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
                getMyXpHistoriesService
        );
    }

    @Test
    @DisplayName(
            "비활성 사용자는 403 USER_NOT_ACTIVE를 반환한다"
    )
    void getMyXpHistories_rejectsInactiveUser() throws Exception {
        when(
                getMyXpHistoriesService
                        .getMyXpHistories(
                                USER_ID,
                                new GetMyXpHistoriesQuery(
                                        20,
                                        null
                                )
                        )
        )
                .thenThrow(
                        new BusinessException(
                                ErrorCode.USER_NOT_ACTIVE
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
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
    void getMyXpHistories_rejectsMissingUser() throws Exception {
        when(
                getMyXpHistoriesService
                        .getMyXpHistories(
                                USER_ID,
                                new GetMyXpHistoriesQuery(
                                        20,
                                        null
                                )
                        )
        )
                .thenThrow(
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/users/me/xp-histories"
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
                            authorization ->
                                    authorization
                                            .anyRequest()
                                            .permitAll()
                    );

            return http.build();
        }
    }
}
