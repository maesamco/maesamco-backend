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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserApiController의 관심 개념 설정 HTTP 계약을 검증합니다.
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
        UserApiControllerUpdateMyInterestsTest
                .TestSecurityConfiguration.class
})
class UserApiControllerUpdateMyInterestsTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID CONCEPT_ID_1 =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID CONCEPT_ID_2 =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final Instant UPDATED_AT =
            Instant.parse("2026-09-15T06:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UpdateMyInterestsService updateMyInterestsService;

    @MockitoBean
    private UpdateMyProfileService updateMyProfileService;

    @MockitoBean
    private GetMyProfileService getMyProfileService;

    @MockitoBean
    private ChangePasswordService changePasswordService;

    @MockitoBean
    private WithdrawUserService withdrawUserService;

    @Test
    @DisplayName(
            "인증된 사용자가 관심 개념을 설정하면 "
                    + "200과 최종 관심 개념 목록을 반환한다"
    )
    void updateMyInterests() throws Exception {
        UpdateMyInterestsCommand command =
                validCommand();

        when(
                updateMyInterestsService.updateMyInterests(
                        USER_ID,
                        command
                )
        ).thenReturn(
                new UpdateMyInterestsResult(
                        List.of(
                                CONCEPT_ID_1,
                                CONCEPT_ID_2
                        ),
                        2,
                        UPDATED_AT
                )
        );

        mockMvc.perform(
                        authenticatedRequest(
                                validRequest()
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
                        jsonPath(
                                "$.data.interestConceptIds[0]"
                        ).value(CONCEPT_ID_1.toString())
                )
                .andExpect(
                        jsonPath(
                                "$.data.interestConceptIds[1]"
                        ).value(CONCEPT_ID_2.toString())
                )
                .andExpect(
                        jsonPath("$.data.count")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.data.updatedAt")
                                .value(UPDATED_AT.toString())
                );

        verify(updateMyInterestsService)
                .updateMyInterests(
                        USER_ID,
                        command
                );
    }

    @Test
    @DisplayName(
            "빈 배열을 전달하면 관심 개념 전체 해제 결과를 반환한다"
    )
    void clearMyInterests() throws Exception {
        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        List.of()
                );

        when(
                updateMyInterestsService.updateMyInterests(
                        USER_ID,
                        command
                )
        ).thenReturn(
                new UpdateMyInterestsResult(
                        List.of(),
                        0,
                        UPDATED_AT
                )
        );

        mockMvc.perform(
                        authenticatedRequest(
                                """
                                {
                                  "conceptIds": []
                                }
                                """
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
                        jsonPath(
                                "$.data.interestConceptIds"
                        ).isEmpty()
                )
                .andExpect(
                        jsonPath("$.data.count")
                                .value(0)
                );

        verify(updateMyInterestsService)
                .updateMyInterests(
                        USER_ID,
                        command
                );
    }

    @Test
    @DisplayName(
            "conceptIds 필드가 누락되면 "
                    + "400 INVALID_INPUT_VALUE를 반환한다"
    )
    void missingConceptIds() throws Exception {
        mockMvc.perform(
                        authenticatedRequest("{}")
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
                                        hasItem("conceptIds")
                                )
                );

        verifyNoInteractions(
                updateMyInterestsService
        );
    }

    @Test
    @DisplayName(
            "conceptIds에 null을 전달하면 "
                    + "400 INVALID_INPUT_VALUE를 반환한다"
    )
    void nullConceptIds() throws Exception {
        mockMvc.perform(
                        authenticatedRequest(
                                """
                                {
                                  "conceptIds": null
                                }
                                """
                        )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("INVALID_INPUT_VALUE")
                );

        verifyNoInteractions(
                updateMyInterestsService
        );
    }

    @Test
    @DisplayName(
            "UUID 형식이 아닌 개념 ID를 전달하면 "
                    + "400 INVALID_INPUT_VALUE를 반환한다"
    )
    void invalidConceptIdFormat() throws Exception {
        mockMvc.perform(
                        authenticatedRequest(
                                """
                                {
                                  "conceptIds": [
                                    "java-loop"
                                  ]
                                }
                                """
                        )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("INVALID_INPUT_VALUE")
                );

        verifyNoInteractions(
                updateMyInterestsService
        );
    }

    @Test
    @DisplayName(
            "수정할 수 없는 userId를 전달하면 "
                    + "400 INVALID_INPUT_VALUE로 거부한다"
    )
    void protectedUserIdField() throws Exception {
        mockMvc.perform(
                        authenticatedRequest(
                                """
                                {
                                  "conceptIds": [],
                                  "userId":
                                    "99999999-9999-9999-9999-999999999999"
                                }
                                """
                        )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("INVALID_INPUT_VALUE")
                );

        verifyNoInteractions(
                updateMyInterestsService
        );
    }

    @Test
    @DisplayName(
            "인증 정보 없이 요청하면 "
                    + "401 AUTH_UNAUTHORIZED를 반환한다"
    )
    void missingAuthentication() throws Exception {
        mockMvc.perform(
                        put("/api/v1/users/me/interests")
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
                updateMyInterestsService
        );
    }

    @Test
    @DisplayName(
            "인증 principal이 UUID가 아니면 "
                    + "401 AUTH_INVALID_TOKEN을 반환한다"
    )
    void invalidAuthenticationPrincipal() throws Exception {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(
                        "invalid-user-id",
                        null,
                        List.of()
                );

        mockMvc.perform(
                        put("/api/v1/users/me/interests")
                                .with(
                                        authentication(token)
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
                updateMyInterestsService
        );
    }

    @Test
    @DisplayName(
            "사용할 수 없는 개념이 포함되면 "
                    + "404 CONCEPT_NOT_FOUND를 반환한다"
    )
    void conceptNotFound() throws Exception {
        stubServiceFailure(
                ErrorCode.CONCEPT_NOT_FOUND
        );

        mockMvc.perform(
                        authenticatedRequest(
                                validRequest()
                        )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("CONCEPT_NOT_FOUND")
                );
    }

    @Test
    @DisplayName(
            "Content Service 연동에 실패하면 "
                    + "503 CONTENT_SERVICE_UNAVAILABLE을 반환한다"
    )
    void contentServiceUnavailable() throws Exception {
        stubServiceFailure(
                ErrorCode.CONTENT_SERVICE_UNAVAILABLE
        );

        mockMvc.perform(
                        authenticatedRequest(
                                validRequest()
                        )
                )
                .andExpect(
                        status().isServiceUnavailable()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "CONTENT_SERVICE_UNAVAILABLE"
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
                        authenticatedRequest(
                                validRequest()
                        )
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("USER_NOT_ACTIVE")
                );
    }

    private void stubServiceFailure(
            ErrorCode errorCode
    ) {
        when(
                updateMyInterestsService.updateMyInterests(
                        USER_ID,
                        validCommand()
                )
        ).thenThrow(
                new BusinessException(errorCode)
        );
    }

    private UpdateMyInterestsCommand validCommand() {
        return new UpdateMyInterestsCommand(
                List.of(
                        CONCEPT_ID_1,
                        CONCEPT_ID_2
                )
        );
    }

    private String validRequest() {
        return """
                {
                  "conceptIds": [
                    "22222222-2222-2222-2222-222222222222",
                    "33333333-3333-3333-3333-333333333333"
                  ]
                }
                """;
    }

    private MockHttpServletRequestBuilder authenticatedRequest(
            String request
    ) {
        return put("/api/v1/users/me/interests")
                .with(
                        authentication(
                                new UsernamePasswordAuthenticationToken(
                                        USER_ID,
                                        null,
                                        List.of()
                                )
                        )
                )
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .content(request);
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
                    .authorizeHttpRequests(auth ->
                            auth.anyRequest().permitAll()
                    );

            return http.build();
        }
    }
}
