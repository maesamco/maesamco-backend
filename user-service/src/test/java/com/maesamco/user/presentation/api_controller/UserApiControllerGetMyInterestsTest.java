package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ChangePasswordRetryService;
import com.maesamco.user.application.service.GetMyGamificationService;
import com.maesamco.user.application.service.GetMyInterestsResult;
import com.maesamco.user.application.service.GetMyInterestsService;
import com.maesamco.user.application.service.GetMyProfileService;
import com.maesamco.user.application.service.GetMyXpHistoriesService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserApiController.class)
@Import({
        AuthCookieConfig.class,
        GlobalExceptionHandler.class,
        UserApiControllerGetMyInterestsTest.TestSecurityConfiguration.class
})
class UserApiControllerGetMyInterestsTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CONCEPT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CONCEPT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMyInterestsService getMyInterestsService;

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
    @DisplayName("인증된 사용자가 조회하면 관심 개념 ID 목록과 개수를 반환한다")
    void getMyInterests_returnsConceptIds() throws Exception {
        when(getMyInterestsService.getMyInterests(USER_ID))
                .thenReturn(GetMyInterestsResult.of(List.of(CONCEPT_A, CONCEPT_B)));

        mockMvc.perform(get("/api/v1/users/me/interests").with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.interestConceptIds[0]").value(CONCEPT_A.toString()))
                .andExpect(jsonPath("$.data.interestConceptIds[1]").value(CONCEPT_B.toString()))
                .andExpect(jsonPath("$.data.count").value(2));

        verify(getMyInterestsService).getMyInterests(USER_ID);
    }

    @Test
    @DisplayName("관심 개념이 없으면 빈 배열과 0을 반환한다")
    void getMyInterests_returnsEmpty() throws Exception {
        when(getMyInterestsService.getMyInterests(USER_ID)).thenReturn(GetMyInterestsResult.of(List.of()));

        mockMvc.perform(get("/api/v1/users/me/interests").with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interestConceptIds").isEmpty())
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    @DisplayName("인증 정보가 없으면 401 AUTH_UNAUTHORIZED를 반환한다")
    void getMyInterests_returns401WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/interests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));

        verifyNoInteractions(getMyInterestsService);
    }

    @Test
    @DisplayName("활성 상태가 아닌 사용자는 403 USER_NOT_ACTIVE를 반환한다")
    void getMyInterests_returns403WhenUserNotActive() throws Exception {
        when(getMyInterestsService.getMyInterests(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE));

        mockMvc.perform(get("/api/v1/users/me/interests").with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("사용자가 없으면 404 USER_NOT_FOUND를 반환한다")
    void getMyInterests_returns404WhenUserMissing() throws Exception {
        when(getMyInterestsService.getMyInterests(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/users/me/interests").with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    private UsernamePasswordAuthenticationToken createAuthentication(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
