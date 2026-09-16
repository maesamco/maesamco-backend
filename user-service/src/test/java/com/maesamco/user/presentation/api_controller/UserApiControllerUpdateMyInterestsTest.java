package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ChangePasswordRetryService;
import com.maesamco.user.application.service.GetMyProfileService;
import com.maesamco.user.application.service.UpdateMyInterestsCommand;
import com.maesamco.user.application.service.UpdateMyInterestsResult;
import com.maesamco.user.application.service.UpdateMyInterestsService;
import com.maesamco.user.application.service.UpdateMyProfileService;
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
 * UserApiController??愿??媛쒕뀗 ?ㅼ젙 HTTP 怨꾩빟??寃利앺빀?덈떎.
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
    private ChangePasswordRetryService changePasswordRetryService;

    @Test
    @DisplayName(
            "?몄쬆???ъ슜?먭? 愿??媛쒕뀗???ㅼ젙?섎㈃ "
                    + "200怨?理쒖쥌 愿??媛쒕뀗 紐⑸줉??諛섑솚?쒕떎"
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
            "鍮?諛곗뿴???꾨떖?섎㈃ 愿??媛쒕뀗 ?꾩껜 ?댁젣 寃곌낵瑜?諛섑솚?쒕떎"
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
            "conceptIds ?꾨뱶媛 ?꾨씫?섎㈃ "
                    + "400 INVALID_INPUT_VALUE瑜?諛섑솚?쒕떎"
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
            "conceptIds??null???꾨떖?섎㈃ "
                    + "400 INVALID_INPUT_VALUE瑜?諛섑솚?쒕떎"
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
            "UUID ?뺤떇???꾨땶 媛쒕뀗 ID瑜??꾨떖?섎㈃ "
                    + "400 INVALID_INPUT_VALUE瑜?諛섑솚?쒕떎"
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
            "?섏젙?????녿뒗 userId瑜??꾨떖?섎㈃ "
                    + "400 INVALID_INPUT_VALUE濡?嫄곕??쒕떎"
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
            "?몄쬆 ?뺣낫 ?놁씠 ?붿껌?섎㈃ "
                    + "401 AUTH_UNAUTHORIZED瑜?諛섑솚?쒕떎"
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
            "?몄쬆 principal??UUID媛 ?꾨땲硫?"
                    + "401 AUTH_INVALID_TOKEN??諛섑솚?쒕떎"
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
            "?ъ슜?????녿뒗 媛쒕뀗???ы븿?섎㈃ "
                    + "404 CONCEPT_NOT_FOUND瑜?諛섑솚?쒕떎"
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
            "Content Service ?곕룞???ㅽ뙣?섎㈃ "
                    + "503 CONTENT_SERVICE_UNAVAILABLE??諛섑솚?쒕떎"
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
            "?쒖꽦 ?곹깭媛 ?꾨땶 ?ъ슜?먮뒗 "
                    + "403 USER_NOT_ACTIVE瑜?諛섑솚?쒕떎"
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

