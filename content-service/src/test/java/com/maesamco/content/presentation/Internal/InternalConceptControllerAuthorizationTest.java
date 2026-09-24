package com.maesamco.content.presentation.Internal;

import com.maesamco.content.application.persistence_service.ConceptValidationInternalService;
import com.maesamco.content.application.result.ConceptValidationInternalResult;
import com.maesamco.content.global.exception.GlobalExceptionHandler;
import com.maesamco.content.global.security.hmac.HmacSignatureUtil;
import com.maesamco.content.global.security.hmac.HmacVerificationFilter;
import com.maesamco.content.global.security.hmac.InternalCallHeaders;
import com.maesamco.content.global.security.hmac.InternalCallerAuthorizationInterceptor;
import com.maesamco.content.global.security.hmac.InternalServiceKeyProperties;
import com.maesamco.content.presentation.internal_controller.InternalConceptController;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code InternalConceptController}의 내부 서비스 호출 권한을 검증한다(이슈 #309).
 *
 * <p>{@code InternalProblemControllerAuthorizationTest}와 동일한 패턴 — 실제 Redis로
 * HMAC 서명 검증(인증)과 {@code @AllowedInternalCallers}(인가)를 end-to-end로 확인한다.</p>
 */
@DisplayName("InternalConceptController 내부 서비스 호출 권한 테스트")
class InternalConceptControllerAuthorizationTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private static final String ALLOWED_CALLER = "user-service";
    private static final String ALLOWED_SECRET = "test-secret-user-to-content";

    private static final String DISALLOWED_CALLER = "some-other-service";
    private static final String DISALLOWED_SECRET = "other-secret-key";

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private MockMvc mockMvc;
    private ConceptValidationInternalService conceptValidationInternalService;

    @BeforeAll
    static void startRedis() {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7"))
                .withExposedPorts(6379);
        redisContainer.start();

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379)
        );

        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
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
        conceptValidationInternalService = Mockito.mock(ConceptValidationInternalService.class);

        InternalConceptController controller =
                new InternalConceptController(conceptValidationInternalService);

        InternalServiceKeyProperties keyProperties = new InternalServiceKeyProperties(
                Map.of(
                        ALLOWED_CALLER, ALLOWED_SECRET,
                        DISALLOWED_CALLER, DISALLOWED_SECRET
                )
        );

        HmacVerificationFilter hmacVerificationFilter =
                new HmacVerificationFilter(keyProperties, redisTemplate);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(hmacVerificationFilter, "/internal/v1/*")
                .addInterceptors(new InternalCallerAuthorizationInterceptor())
                .build();
    }

    @Nested
    @DisplayName("허용된 내부 서비스 호출")
    class AllowedInternalCaller {

        @Test
        @DisplayName("user-service의 유효한 HMAC 서명 요청은 개념 검증에 성공한다")
        void acceptsUserService() throws Exception {

            // given
            UUID conceptId = UUID.randomUUID();
            String path = "/internal/v1/concepts/validate";
            String body = JSON_MAPPER.writeValueAsString(Map.of("conceptIds", List.of(conceptId)));

            ConceptValidationInternalResult result =
                    new ConceptValidationInternalResult(true, List.of(conceptId), List.of());

            given(conceptValidationInternalService.validate(List.of(conceptId)))
                    .willReturn(result);

            HmacHeaders headers = createValidHeaders(ALLOWED_CALLER, ALLOWED_SECRET, "POST", path, body);

            // when & then
            mockMvc.perform(
                            post(path)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .header(InternalCallHeaders.SERVICE, ALLOWED_CALLER)
                                    .header(InternalCallHeaders.TIMESTAMP, headers.timestamp())
                                    .header(InternalCallHeaders.NONCE, headers.nonce())
                                    .header(InternalCallHeaders.SIGNATURE, headers.signature())
                    )
                    .andExpect(status().isOk());

            Mockito.verify(conceptValidationInternalService).validate(List.of(conceptId));
            Mockito.verifyNoMoreInteractions(conceptValidationInternalService);
        }
    }

    @Nested
    @DisplayName("허용되지 않은 내부 서비스 호출")
    class DisallowedInternalCaller {

        @Test
        @DisplayName("유효한 HMAC 서명을 사용해도 허용되지 않은 서비스는 개념 검증이 403으로 거부된다")
        void rejectsDisallowedCallerDespiteValidSignature() throws Exception {

            // given
            UUID conceptId = UUID.randomUUID();
            String path = "/internal/v1/concepts/validate";
            String body = JSON_MAPPER.writeValueAsString(Map.of("conceptIds", List.of(conceptId)));

            HmacHeaders headers =
                    createValidHeaders(DISALLOWED_CALLER, DISALLOWED_SECRET, "POST", path, body);

            // when & then
            mockMvc.perform(
                            post(path)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .header(InternalCallHeaders.SERVICE, DISALLOWED_CALLER)
                                    .header(InternalCallHeaders.TIMESTAMP, headers.timestamp())
                                    .header(InternalCallHeaders.NONCE, headers.nonce())
                                    .header(InternalCallHeaders.SIGNATURE, headers.signature())
                    )
                    .andExpect(status().isForbidden());

            verifyNoInteractions(conceptValidationInternalService);
        }
    }

    private HmacHeaders createValidHeaders(
            String callerService, String secretKey, String method, String path, String body
    ) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String bodyHash = HmacSignatureUtil.hashBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String signature = HmacSignatureUtil.sign(
                callerService, method, path, "", bodyHash, nonce, timestamp, secretKey
        );

        return new HmacHeaders(String.valueOf(timestamp), nonce, signature);
    }

    private record HmacHeaders(String timestamp, String nonce, String signature) {
    }
}
