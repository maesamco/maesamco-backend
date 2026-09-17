package com.maesamco.user.presentation.internal_controller;

import com.maesamco.user.application.service.GetInternalUserService;
import com.maesamco.user.application.service.GetQuizTargetUsersQuery;
import com.maesamco.user.application.service.GetQuizTargetUsersResult;
import com.maesamco.user.application.service.GetQuizTargetUsersService;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.exception.GlobalExceptionHandler;
import com.maesamco.user.global.security.hmac.HmacSignatureUtil;
import com.maesamco.user.global.security.hmac.HmacVerificationFilter;
import com.maesamco.user.global.security.hmac.InternalCallHeaders;
import com.maesamco.user.global.security.hmac.InternalCallerAuthorizationInterceptor;
import com.maesamco.user.global.security.hmac.InternalServiceKeyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserInternalControllerHmacTest {

    private static final String QUIZ_TARGETS_PATH =
            "/internal/v1/users/quiz-targets";

    private static final String RAW_QUERY =
            "size=1";

    private static final String CONTENT_SERVICE =
            "content-service";

    private static final String JUDGE_SERVICE =
            "judge-service";

    private static final String CONTENT_SECRET =
            "test-content-service-secret";

    private static final String JUDGE_SECRET =
            "test-judge-service-secret";

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Mock
    private GetInternalUserService getInternalUserService;

    @Mock
    private GetQuizTargetUsersService getQuizTargetUsersService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternalServiceKeyProperties keyProperties =
                new InternalServiceKeyProperties(
                        Map.of(
                                CONTENT_SERVICE,
                                CONTENT_SECRET,
                                JUDGE_SERVICE,
                                JUDGE_SECRET
                        )
                );

        HmacVerificationFilter hmacVerificationFilter =
                new HmacVerificationFilter(
                        keyProperties,
                        redisTemplate
                );

        UserInternalController controller =
                new UserInternalController(
                        getInternalUserService,
                        getQuizTargetUsersService
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(controller)
                        .setControllerAdvice(
                                new GlobalExceptionHandler()
                        )
                        .addInterceptors(
                                new InternalCallerAuthorizationInterceptor()
                        )
                        .addFilters(
                                hmacVerificationFilter
                        )
                        .build();
    }

    @Test
    @DisplayName(
            "유효한 Content Service HMAC 요청은 "
                    + "내부 API를 정상 호출한다"
    )
    void validHmacRequestPasses()
            throws Exception {
        // given
        String nonce =
                "valid-content-request";

        long timestamp =
                System.currentTimeMillis();

        GetQuizTargetUsersQuery expectedQuery =
                new GetQuizTargetUsersQuery(
                        null,
                        1
                );

        GetQuizTargetUsersResult result =
                new GetQuizTargetUsersResult(
                        List.of(USER_ID),
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

        allowNonceOnce(
                CONTENT_SERVICE,
                nonce
        );

        // when & then
        mockMvc.perform(
                        signedQuizTargetsRequest(
                                CONTENT_SERVICE,
                                CONTENT_SECRET,
                                nonce,
                                timestamp
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
                        jsonPath("$.data.userIds[0]")
                                .value(USER_ID.toString())
                )
                .andExpect(
                        jsonPath("$.data.nextCursor")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.hasNext")
                                .value(false)
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
            "HMAC 헤더가 누락된 요청은 "
                    + "401을 반환한다"
    )
    void missingHmacHeadersReturnsUnauthorized()
            throws Exception {
        mockMvc.perform(
                        get(
                                QUIZ_TARGETS_PATH
                                        + "?"
                                        + RAW_QUERY
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
                                        "INTERNAL_CALL_SIGNATURE_INVALID"
                                )
                );

        verifyNoInteractions(
                redisTemplate,
                valueOperations,
                getInternalUserService,
                getQuizTargetUsersService
        );
    }

    @Test
    @DisplayName(
            "변조된 HMAC 서명은 "
                    + "401을 반환한다"
    )
    void tamperedSignatureReturnsUnauthorized()
            throws Exception {
        // given
        String nonce =
                "tampered-signature-request";

        long timestamp =
                System.currentTimeMillis();

        // when & then
        mockMvc.perform(
                        quizTargetsRequest(
                                CONTENT_SERVICE,
                                nonce,
                                timestamp,
                                "tampered-signature"
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
                                        "INTERNAL_CALL_SIGNATURE_INVALID"
                                )
                );

        verifyNoInteractions(
                redisTemplate,
                valueOperations,
                getInternalUserService,
                getQuizTargetUsersService
        );
    }

    @Test
    @DisplayName(
            "허용 시간을 지난 HMAC 요청은 "
                    + "401을 반환한다"
    )
    void expiredTimestampReturnsUnauthorized()
            throws Exception {
        // given
        String nonce =
                "expired-request";

        long expiredTimestamp =
                System.currentTimeMillis()
                        - Duration.ofMinutes(6)
                        .toMillis();

        // when & then
        mockMvc.perform(
                        signedQuizTargetsRequest(
                                CONTENT_SERVICE,
                                CONTENT_SECRET,
                                nonce,
                                expiredTimestamp
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
                                        "INTERNAL_CALL_SIGNATURE_INVALID"
                                )
                );

        verifyNoInteractions(
                redisTemplate,
                valueOperations,
                getInternalUserService,
                getQuizTargetUsersService
        );
    }

    @Test
    @DisplayName(
            "동일한 nonce를 재사용한 요청은 "
                    + "두 번째 호출에서 401을 반환한다"
    )
    void replayedNonceReturnsUnauthorized()
            throws Exception {
        // given
        String nonce =
                "replayed-content-request";

        long timestamp =
                System.currentTimeMillis();

        GetQuizTargetUsersQuery expectedQuery =
                new GetQuizTargetUsersQuery(
                        null,
                        1
                );

        GetQuizTargetUsersResult result =
                new GetQuizTargetUsersResult(
                        List.of(USER_ID),
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

        when(
                redisTemplate.opsForValue()
        ).thenReturn(
                valueOperations
        );

        when(
                valueOperations.setIfAbsent(
                        eq(
                                nonceKey(
                                        CONTENT_SERVICE,
                                        nonce
                                )
                        ),
                        eq("1"),
                        any(Duration.class)
                )
        ).thenReturn(
                true,
                false
        );

        MockHttpServletRequestBuilder request =
                signedQuizTargetsRequest(
                        CONTENT_SERVICE,
                        CONTENT_SECRET,
                        nonce,
                        timestamp
                );

        // when & then
        mockMvc.perform(request)
                .andExpect(
                        status().isOk()
                );

        mockMvc.perform(
                        signedQuizTargetsRequest(
                                CONTENT_SERVICE,
                                CONTENT_SECRET,
                                nonce,
                                timestamp
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
                                        "INTERNAL_CALL_SIGNATURE_INVALID"
                                )
                );

        verify(
                getQuizTargetUsersService,
                times(1)
        ).getQuizTargetUsers(
                expectedQuery
        );

        verifyNoInteractions(
                getInternalUserService
        );
    }

    @Test
    @DisplayName(
            "허용되지 않은 내부 서비스의 유효한 HMAC 요청은 "
                    + "INTERNAL_CALLER_NOT_ALLOWED를 반환한다"
    )
    void validSignatureFromUnauthorizedServiceIsRejected()
            throws Exception {
        // given
        String nonce =
                "valid-judge-request";

        long timestamp =
                System.currentTimeMillis();

        allowNonceOnce(
                JUDGE_SERVICE,
                nonce
        );

        // when & then
        mockMvc.perform(
                        signedQuizTargetsRequest(
                                JUDGE_SERVICE,
                                JUDGE_SECRET,
                                nonce,
                                timestamp
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
                getInternalUserService,
                getQuizTargetUsersService
        );
    }

    private void allowNonceOnce(
            String callerService,
            String nonce
    ) {
        when(
                redisTemplate.opsForValue()
        ).thenReturn(
                valueOperations
        );

        when(
                valueOperations.setIfAbsent(
                        eq(
                                nonceKey(
                                        callerService,
                                        nonce
                                )
                        ),
                        eq("1"),
                        any(Duration.class)
                )
        ).thenReturn(
                true
        );
    }

    private MockHttpServletRequestBuilder
    signedQuizTargetsRequest(
            String callerService,
            String secret,
            String nonce,
            long timestamp
    ) {
        String signature =
                HmacSignatureUtil.sign(
                        callerService,
                        "GET",
                        QUIZ_TARGETS_PATH,
                        HmacSignatureUtil.normalizeQuery(
                                RAW_QUERY
                        ),
                        HmacSignatureUtil.hashBody(null),
                        nonce,
                        timestamp,
                        secret
                );

        return quizTargetsRequest(
                callerService,
                nonce,
                timestamp,
                signature
        );
    }

    private MockHttpServletRequestBuilder
    quizTargetsRequest(
            String callerService,
            String nonce,
            long timestamp,
            String signature
    ) {
        return get(
                QUIZ_TARGETS_PATH
                        + "?"
                        + RAW_QUERY
        )
                .header(
                        InternalCallHeaders.SERVICE,
                        callerService
                )
                .header(
                        InternalCallHeaders.TIMESTAMP,
                        Long.toString(timestamp)
                )
                .header(
                        InternalCallHeaders.NONCE,
                        nonce
                )
                .header(
                        InternalCallHeaders.SIGNATURE,
                        signature
                );
    }

    private String nonceKey(
            String callerService,
            String nonce
    ) {
        return "hmac-nonce:"
                + callerService
                + ":"
                + nonce;
    }
}
