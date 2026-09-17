package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.result.ProblemInternalResult;
import com.maesamco.content.application.service.ProblemInternalService;
import com.maesamco.content.global.exception.GlobalExceptionHandler;
import com.maesamco.content.global.security.hmac.HmacSignatureUtil;
import com.maesamco.content.global.security.hmac.HmacVerificationFilter;
import com.maesamco.content.global.security.hmac.InternalCallHeaders;
import com.maesamco.content.global.security.hmac.InternalCallerAuthorizationInterceptor;
import com.maesamco.content.global.security.hmac.InternalServiceKeyProperties;
import com.maesamco.content.presentation.internal_controller.InternalProblemController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("InternalProblemController 내부 서비스 호출 권한 테스트")
class InternalProblemControllerAuthorizationTest {

    private static final String ALLOWED_CALLER = "coaching-service";
    private static final String ALLOWED_SECRET =
            "test-secret-coaching-to-content";

    private static final String DISALLOWED_CALLER =
            "some-other-service";
    private static final String DISALLOWED_SECRET =
            "other-secret-key";

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private MockMvc mockMvc;
    private ProblemInternalService problemInternalService;

    @BeforeAll
    static void startRedis() {

        redisContainer =
                new GenericContainer<>(
                        DockerImageName.parse("redis:7")
                )
                        .withExposedPorts(6379);

        redisContainer.start();

        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(
                        redisContainer.getHost(),
                        redisContainer.getMappedPort(6379)
                );

        connectionFactory =
                new LettuceConnectionFactory(configuration);

        connectionFactory.afterPropertiesSet();

        redisTemplate =
                new StringRedisTemplate(connectionFactory);

        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {

        if (connectionFactory != null) {
            connectionFactory.destroy();
        }

        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    @BeforeEach
    void setUp() {

        problemInternalService =
                Mockito.mock(ProblemInternalService.class);

        InternalProblemController controller =
                new InternalProblemController(
                        problemInternalService
                );

        InternalServiceKeyProperties keyProperties =
                new InternalServiceKeyProperties(
                        Map.of(
                                ALLOWED_CALLER,
                                ALLOWED_SECRET,
                                DISALLOWED_CALLER,
                                DISALLOWED_SECRET
                        )
                );

        HmacVerificationFilter hmacVerificationFilter =
                new HmacVerificationFilter(
                        keyProperties,
                        redisTemplate
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(controller)
                        .setControllerAdvice(
                                new GlobalExceptionHandler()
                        )
                        .addFilter(
                                hmacVerificationFilter,
                                "/internal/v1/*"
                        )
                        .addInterceptors(
                                new InternalCallerAuthorizationInterceptor()
                        )
                        .build();
    }

    @Nested
    @DisplayName("허용된 내부 서비스 호출")
    class AllowedInternalCaller {

        @Test
        @DisplayName("coaching-service의 유효한 HMAC 서명 요청은 문제 메타데이터 조회에 성공한다")
        void acceptsCoachingService() throws Exception {

            // given
            UUID problemId = UUID.randomUUID();

            String path =
                    "/internal/v1/problems/" + problemId;

            ProblemInternalResult result =
                    new ProblemInternalResult(
                            problemId,
                            "설명",
                            List.of("배열")
                    );

            given(
                    problemInternalService
                            .getProblemMetaData(problemId)
            )
                    .willReturn(result);

            HmacHeaders headers =
                    createValidHeaders(
                            ALLOWED_CALLER,
                            ALLOWED_SECRET,
                            "GET",
                            path
                    );

            // when & then
            mockMvc.perform(
                            get(path)
                                    .header(
                                            InternalCallHeaders.SERVICE,
                                            ALLOWED_CALLER
                                    )
                                    .header(
                                            InternalCallHeaders.TIMESTAMP,
                                            headers.timestamp()
                                    )
                                    .header(
                                            InternalCallHeaders.NONCE,
                                            headers.nonce()
                                    )
                                    .header(
                                            InternalCallHeaders.SIGNATURE,
                                            headers.signature()
                                    )
                    )
                    .andExpect(status().isOk());

            Mockito.verify(problemInternalService)
                    .getProblemMetaData(problemId);

            Mockito.verifyNoMoreInteractions(
                    problemInternalService
            );
        }
    }

    @Nested
    @DisplayName("허용되지 않은 내부 서비스 호출")
    class DisallowedInternalCaller {

        @Test
        @DisplayName("유효한 HMAC 서명을 사용해도 허용되지 않은 서비스는 문제 메타데이터 조회가 403으로 거부된다")
        void rejectsDisallowedCallerDespiteValidSignature()
                throws Exception {

            // given
            UUID problemId = UUID.randomUUID();

            String path =
                    "/internal/v1/problems/" + problemId;

            HmacHeaders headers =
                    createValidHeaders(
                            DISALLOWED_CALLER,
                            DISALLOWED_SECRET,
                            "GET",
                            path
                    );

            // when & then
            mockMvc.perform(
                            get(path)
                                    .header(
                                            InternalCallHeaders.SERVICE,
                                            DISALLOWED_CALLER
                                    )
                                    .header(
                                            InternalCallHeaders.TIMESTAMP,
                                            headers.timestamp()
                                    )
                                    .header(
                                            InternalCallHeaders.NONCE,
                                            headers.nonce()
                                    )
                                    .header(
                                            InternalCallHeaders.SIGNATURE,
                                            headers.signature()
                                    )
                    )
                    .andExpect(status().isForbidden());

            verifyNoInteractions(problemInternalService);
        }

        @Test
        @DisplayName("problem-versions 조회도 허용되지 않은 서비스의 요청은 403으로 거부된다")
        void rejectsDisallowedCallerForProblemVersionEndpoint()
                throws Exception {

            // given
            UUID problemVersionId = UUID.randomUUID();

            String path =
                    "/internal/v1/problem-versions/"
                            + problemVersionId;

            HmacHeaders headers =
                    createValidHeaders(
                            DISALLOWED_CALLER,
                            DISALLOWED_SECRET,
                            "GET",
                            path
                    );

            // when & then
            mockMvc.perform(
                            get(path)
                                    .header(
                                            InternalCallHeaders.SERVICE,
                                            DISALLOWED_CALLER
                                    )
                                    .header(
                                            InternalCallHeaders.TIMESTAMP,
                                            headers.timestamp()
                                    )
                                    .header(
                                            InternalCallHeaders.NONCE,
                                            headers.nonce()
                                    )
                                    .header(
                                            InternalCallHeaders.SIGNATURE,
                                            headers.signature()
                                    )
                    )
                    .andExpect(status().isForbidden());

            verifyNoInteractions(problemInternalService);
        }
    }

    private HmacHeaders createValidHeaders(
            String callerService,
            String secretKey,
            String method,
            String path
    ) {

        long timestamp =
                System.currentTimeMillis();

        String nonce =
                UUID.randomUUID().toString();

        String bodyHash =
                HmacSignatureUtil.hashBody(null);

        String signature =
                HmacSignatureUtil.sign(
                        callerService,
                        method,
                        path,
                        "",
                        bodyHash,
                        nonce,
                        timestamp,
                        secretKey
                );

        return new HmacHeaders(
                String.valueOf(timestamp),
                nonce,
                signature
        );
    }

    private record HmacHeaders(
            String timestamp,
            String nonce,
            String signature
    ) {
    }
}