package com.maesamco.user.presentation.internal_controller;

import com.maesamco.user.application.service.GetInternalUserResult;
import com.maesamco.user.application.service.GetInternalUserService;
import com.maesamco.user.application.service.GetQuizTargetUsersQuery;
import com.maesamco.user.application.service.GetQuizTargetUsersResult;
import com.maesamco.user.application.service.GetQuizTargetUsersService;
import com.maesamco.user.global.config.InternalCallerAuthorizationConfig;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.exception.GlobalExceptionHandler;
import com.maesamco.user.global.security.hmac.InternalCallHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserInternalController.class)
@Import({
        GlobalExceptionHandler.class,
        InternalCallerAuthorizationConfig.class,
        UserInternalControllerTest
                .TestSecurityConfiguration.class
})
class UserInternalControllerTest {

    private static final String CONTENT_SERVICE =
            "content-service";

    private static final String UNAUTHORIZED_SERVICE =
            "judge-service";

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID FIRST_USER_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID SECOND_USER_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final UUID FIRST_CONCEPT_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    private static final UUID SECOND_CONCEPT_ID =
            UUID.fromString(
                    "55555555-5555-5555-5555-555555555555"
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetInternalUserService getInternalUserService;

    @MockitoBean
    private GetQuizTargetUsersService
            getQuizTargetUsersService;

    @Test
    @DisplayName(
            "Content Service가 내부 사용자 정보를 조회하면 "
                    + "200과 관심 개념 목록을 반환한다"
    )
    void getInternalUser_returnsInterestConceptIds()
            throws Exception {
        // given
        GetInternalUserResult result =
                new GetInternalUserResult(
                        List.of(
                                FIRST_CONCEPT_ID,
                                SECOND_CONCEPT_ID
                        )
                );

        when(
                getInternalUserService.getInternalUser(
                        USER_ID
                )
        ).thenReturn(
                result
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/internal/v1/users/{userId}",
                                USER_ID
                        )
                                .header(
                                        InternalCallHeaders.SERVICE,
                                        CONTENT_SERVICE
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
                        jsonPath(
                                "$.data.interestConceptIds.length()"
                        ).value(2)
                )
                .andExpect(
                        jsonPath(
                                "$.data.interestConceptIds[0]"
                        ).value(
                                FIRST_CONCEPT_ID.toString()
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.data.interestConceptIds[1]"
                        ).value(
                                SECOND_CONCEPT_ID.toString()
                        )
                );

        verify(getInternalUserService)
                .getInternalUser(USER_ID);

        verifyNoInteractions(
                getQuizTargetUsersService
        );
    }

    @Test
    @DisplayName(
            "내부 사용자 정보가 존재하지 않으면 "
                    + "404 USER_NOT_FOUND를 반환한다"
    )
    void getInternalUser_returnsNotFound()
            throws Exception {
        // given
        when(
                getInternalUserService.getInternalUser(
                        USER_ID
                )
        ).thenThrow(
                new BusinessException(
                        ErrorCode.USER_NOT_FOUND
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/internal/v1/users/{userId}",
                                USER_ID
                        )
                                .header(
                                        InternalCallHeaders.SERVICE,
                                        CONTENT_SERVICE
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

        verify(getInternalUserService)
                .getInternalUser(USER_ID);
    }

    @Test
    @DisplayName(
            "활성 상태가 아닌 내부 사용자는 "
                    + "USER_NOT_ACTIVE 응답을 반환한다"
    )
    void getInternalUser_returnsUserNotActive()
            throws Exception {
        // given
        when(
                getInternalUserService.getInternalUser(
                        USER_ID
                )
        ).thenThrow(
                new BusinessException(
                        ErrorCode.USER_NOT_ACTIVE
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/internal/v1/users/{userId}",
                                USER_ID
                        )
                                .header(
                                        InternalCallHeaders.SERVICE,
                                        CONTENT_SERVICE
                                )
                )
                .andExpect(
                        status().is(
                                ErrorCode.USER_NOT_ACTIVE
                                        .getStatus()
                                        .value()
                        )
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("USER_NOT_ACTIVE")
                );

        verify(getInternalUserService)
                .getInternalUser(USER_ID);
    }

    @Test
    @DisplayName(
            "첫 Daily Quiz 대상 페이지를 조회하면 "
                    + "사용자 목록과 다음 커서를 반환한다"
    )
    void getQuizTargetUsers_returnsFirstPage()
            throws Exception {
        // given
        GetQuizTargetUsersQuery expectedQuery =
                new GetQuizTargetUsersQuery(
                        null,
                        2
                );

        GetQuizTargetUsersResult result =
                new GetQuizTargetUsersResult(
                        List.of(
                                FIRST_USER_ID,
                                SECOND_USER_ID
                        ),
                        SECOND_USER_ID,
                        true
                );

        when(
                getQuizTargetUsersService
                        .getQuizTargetUsers(
                                expectedQuery
                        )
        ).thenReturn(
                result
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/internal/v1/users/quiz-targets"
                        )
                                .param(
                                        "size",
                                        "2"
                                )
                                .header(
                                        InternalCallHeaders.SERVICE,
                                        CONTENT_SERVICE
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
                        jsonPath("$.data.userIds.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.data.userIds[0]")
                                .value(
                                        FIRST_USER_ID.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.userIds[1]")
                                .value(
                                        SECOND_USER_ID.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.nextCursor")
                                .value(
                                        SECOND_USER_ID.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.data.hasNext")
                                .value(true)
                );

        verify(getQuizTargetUsersService)
                .getQuizTargetUsers(
                        expectedQuery
                );

        verifyNoInteractions(
                getInternalUserService
        );
    }

    @Test
    @DisplayName(
            "후속 Daily Quiz 대상 페이지를 조회하면 "
                    + "요청 커서를 서비스에 전달한다"
    )
    void getQuizTargetUsers_forwardsCursor()
            throws Exception {
        // given
        GetQuizTargetUsersQuery expectedQuery =
                new GetQuizTargetUsersQuery(
                        FIRST_USER_ID,
                        1
                );

        GetQuizTargetUsersResult result =
                new GetQuizTargetUsersResult(
                        List.of(
                                SECOND_USER_ID
                        ),
                        null,
                        false
                );

        when(
                getQuizTargetUsersService
                        .getQuizTargetUsers(
                                expectedQuery
                        )
        ).thenReturn(
                result
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/internal/v1/users/quiz-targets"
                        )
                                .param(
                                        "cursor",
                                        FIRST_USER_ID.toString()
                                )
                                .param(
                                        "size",
                                        "1"
                                )
                                .header(
                                        InternalCallHeaders.SERVICE,
                                        CONTENT_SERVICE
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.data.userIds[0]")
                                .value(
                                        SECOND_USER_ID.toString()
                                )
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

        verify(getQuizTargetUsersService)
                .getQuizTargetUsers(
                        expectedQuery
                );
    }

    @Test
    @DisplayName(
            "size가 누락되면 "
                    + "400 INVALID_INPUT_VALUE를 반환한다"
    )
    void getQuizTargetUsers_rejectsMissingSize()
            throws Exception {
        mockMvc.perform(
                        get(
                                "/internal/v1/users/quiz-targets"
                        )
                                .header(
                                        InternalCallHeaders.SERVICE,
                                        CONTENT_SERVICE
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("INVALID_INPUT_VALUE")
                );

        verifyNoInteractions(
                getQuizTargetUsersService,
                getInternalUserService
        );
    }

    @Test
    @DisplayName(
            "size가 0이면 "
                    + "400 INVALID_INPUT_VALUE를 반환한다"
    )
    void getQuizTargetUsers_rejectsZeroSize()
            throws Exception {
        assertInvalidSize(
                "0"
        );
    }

    @Test
    @DisplayName(
            "size가 최대값을 초과하면 "
                    + "400 INVALID_INPUT_VALUE를 반환한다"
    )
    void getQuizTargetUsers_rejectsOversizedRequest()
            throws Exception {
        assertInvalidSize(
                "1001"
        );
    }

    @Test
    @DisplayName(
            "cursor가 UUID 형식이 아니면 "
                    + "400 INVALID_INPUT_VALUE를 반환한다"
    )
    void getQuizTargetUsers_rejectsMalformedCursor()
            throws Exception {
        mockMvc.perform(
                        get(
                                "/internal/v1/users/quiz-targets"
                        )
                                .param(
                                        "cursor",
                                        "not-a-uuid"
                                )
                                .param(
                                        "size",
                                        "10"
                                )
                                .header(
                                        InternalCallHeaders.SERVICE,
                                        CONTENT_SERVICE
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("INVALID_INPUT_VALUE")
                );

        verifyNoInteractions(
                getQuizTargetUsersService,
                getInternalUserService
        );
    }

    @Test
    @DisplayName(
            "허용되지 않은 내부 서비스가 호출하면 "
                    + "INTERNAL_CALLER_NOT_ALLOWED를 반환한다"
    )
    void rejectsUnauthorizedInternalCaller()
            throws Exception {
        mockMvc.perform(
                        get(
                                "/internal/v1/users/quiz-targets"
                        )
                                .param(
                                        "size",
                                        "10"
                                )
                                .header(
                                        InternalCallHeaders.SERVICE,
                                        UNAUTHORIZED_SERVICE
                                )
                )
                .andExpect(
                        status().is(
                                ErrorCode.INTERNAL_CALLER_NOT_ALLOWED
                                        .getStatus()
                                        .value()
                        )
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INTERNAL_CALLER_NOT_ALLOWED"
                                )
                );

        verifyNoInteractions(
                getQuizTargetUsersService,
                getInternalUserService
        );
    }

    @Test
    @DisplayName(
            "내부 호출자 헤더가 없으면 "
                    + "INTERNAL_CALLER_NOT_ALLOWED를 반환한다"
    )
    void rejectsMissingInternalCaller()
            throws Exception {
        mockMvc.perform(
                        get(
                                "/internal/v1/users/{userId}",
                                USER_ID
                        )
                )
                .andExpect(
                        status().is(
                                ErrorCode.INTERNAL_CALLER_NOT_ALLOWED
                                        .getStatus()
                                        .value()
                        )
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INTERNAL_CALLER_NOT_ALLOWED"
                                )
                );

        verifyNoInteractions(
                getQuizTargetUsersService,
                getInternalUserService
        );
    }

    private void assertInvalidSize(
            String size
    ) throws Exception {
        mockMvc.perform(
                        get(
                                "/internal/v1/users/quiz-targets"
                        )
                                .param(
                                        "size",
                                        size
                                )
                                .header(
                                        InternalCallHeaders.SERVICE,
                                        CONTENT_SERVICE
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value("INVALID_INPUT_VALUE")
                );

        verifyNoInteractions(
                getQuizTargetUsersService,
                getInternalUserService
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
                    .authorizeHttpRequests(auth ->
                            auth.anyRequest().permitAll()
                    );

            return http.build();
        }
    }
}