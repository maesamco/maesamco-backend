package com.maesamco.coaching.presentation.internal_controller;

import com.maesamco.coaching.application.query_service.WeakConceptQueryService;
import com.maesamco.coaching.global.exception.GlobalExceptionHandler;
import com.maesamco.coaching.global.security.hmac.HmacSignatureUtil;
import com.maesamco.coaching.global.security.hmac.HmacVerificationFilter;
import com.maesamco.coaching.global.security.hmac.InternalCallHeaders;
import com.maesamco.coaching.global.security.hmac.InternalServiceKeyProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RedisAiFeedbackRetryLockAdapterTest와 동일한 이유(Mockito로는 Redis SETNX 기반 nonce
 * 재사용 검증을 흉내낼 수 없음) — HmacVerificationFilter를 실제 필터 체인으로 태워서
 * HMAC 검증을 검증한다(PR #124 심층 재검토, 용현님 — 이 필터를 실제로 거치는 테스트가
 * 하나도 없다는 지적 반영).
 *
 * 이 서비스의 첫 실제 `/internal/v1/**` 컨트롤러라 여기서 처음 만든다 — 이후 내부
 * 컨트롤러가 추가되면 이 테스트 구조를 그대로 참고하면 된다.
 */
class WeakConceptInternalControllerSecurityTest {

    private static final String CALLER_SERVICE = "content-service";
    private static final String SECRET_KEY = "test-secret-key-for-hmac-verification";

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private MockMvc mockMvc;
    private WeakConceptQueryService weakConceptQueryService;

    @BeforeAll
    static void startRedis() {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7"))
                .withExposedPorts(6379);
        redisContainer.start();

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379)
        );
        connectionFactory = new LettuceConnectionFactory(config);
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
        weakConceptQueryService = Mockito.mock(WeakConceptQueryService.class);
        WeakConceptInternalController controller = new WeakConceptInternalController(weakConceptQueryService);

        InternalServiceKeyProperties keyProperties =
                new InternalServiceKeyProperties(Map.of(CALLER_SERVICE, SECRET_KEY));
        HmacVerificationFilter hmacVerificationFilter =
                new HmacVerificationFilter(keyProperties, redisTemplate);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(hmacVerificationFilter, "/internal/v1/*")
                .build();
    }

    private String path(UUID userId) {
        return "/internal/v1/users/" + userId + "/weak-concepts";
    }

    /** 실제 서명을 만들어서 정상 호출을 흉내낸다 — HmacFeignInterceptor가 하는 것과 같은 계산. */
    private String[] validHeaders(String method, String path) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String bodyHash = HmacSignatureUtil.hashBody(null);
        String signature = HmacSignatureUtil.sign(
                CALLER_SERVICE, method, path, "", bodyHash, nonce, timestamp, SECRET_KEY);
        return new String[]{String.valueOf(timestamp), nonce, signature};
    }

    @Test
    @DisplayName("HMAC 헤더가 전혀 없으면 401을 반환하고 컨트롤러까지 도달하지 않는다")
    void rejectsRequestWithoutHmacHeaders() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get(path(userId)))
                .andExpect(status().isUnauthorized());

        Mockito.verifyNoInteractions(weakConceptQueryService);
    }

    @Test
    @DisplayName("서명이 유효하지 않으면 401을 반환한다")
    void rejectsInvalidSignature() throws Exception {
        UUID userId = UUID.randomUUID();
        String[] headers = validHeaders("GET", path(userId));

        mockMvc.perform(get(path(userId))
                        .header(InternalCallHeaders.SERVICE, CALLER_SERVICE)
                        .header(InternalCallHeaders.TIMESTAMP, headers[0])
                        .header(InternalCallHeaders.NONCE, headers[1])
                        .header(InternalCallHeaders.SIGNATURE, "invalid-signature-value"))
                .andExpect(status().isUnauthorized());

        Mockito.verifyNoInteractions(weakConceptQueryService);
    }

    @Test
    @DisplayName("정상적으로 서명된 Content Service 요청은 통과해서 컨트롤러가 호출된다")
    void acceptsValidContentServiceSignature() throws Exception {
        UUID userId = UUID.randomUUID();
        String[] headers = validHeaders("GET", path(userId));
        given(weakConceptQueryService.getWeakConcepts(userId)).willReturn(java.util.List.of());

        mockMvc.perform(get(path(userId))
                        .header(InternalCallHeaders.SERVICE, CALLER_SERVICE)
                        .header(InternalCallHeaders.TIMESTAMP, headers[0])
                        .header(InternalCallHeaders.NONCE, headers[1])
                        .header(InternalCallHeaders.SIGNATURE, headers[2]))
                .andExpect(status().isOk());

        Mockito.verify(weakConceptQueryService).getWeakConcepts(userId);
    }

    @Test
    @DisplayName("같은 서명(nonce)을 재전송하면 두 번째 요청은 401로 거부된다")
    void rejectsReplayedNonce() throws Exception {
        UUID userId = UUID.randomUUID();
        String[] headers = validHeaders("GET", path(userId));
        given(weakConceptQueryService.getWeakConcepts(userId)).willReturn(java.util.List.of());

        mockMvc.perform(get(path(userId))
                        .header(InternalCallHeaders.SERVICE, CALLER_SERVICE)
                        .header(InternalCallHeaders.TIMESTAMP, headers[0])
                        .header(InternalCallHeaders.NONCE, headers[1])
                        .header(InternalCallHeaders.SIGNATURE, headers[2]))
                .andExpect(status().isOk());

        // 정확히 같은 헤더(같은 nonce)로 재전송
        mockMvc.perform(get(path(userId))
                        .header(InternalCallHeaders.SERVICE, CALLER_SERVICE)
                        .header(InternalCallHeaders.TIMESTAMP, headers[0])
                        .header(InternalCallHeaders.NONCE, headers[1])
                        .header(InternalCallHeaders.SIGNATURE, headers[2]))
                .andExpect(status().isUnauthorized());

        Mockito.verify(weakConceptQueryService, Mockito.times(1)).getWeakConcepts(userId);
    }

    @Test
    @DisplayName("서명은 유효하지만 Content Service가 아닌 다른 호출자는 403으로 거부된다")
    void rejectsValidSignatureFromDisallowedCaller() throws Exception {
        UUID userId = UUID.randomUUID();
        String otherService = "some-other-service";
        String otherSecret = "other-secret-key";
        InternalServiceKeyProperties keyPropertiesWithOther = new InternalServiceKeyProperties(
                Map.of(CALLER_SERVICE, SECRET_KEY, otherService, otherSecret));
        WeakConceptInternalController controller = new WeakConceptInternalController(weakConceptQueryService);
        HmacVerificationFilter filterWithOtherCaller =
                new HmacVerificationFilter(keyPropertiesWithOther, redisTemplate);
        MockMvc mockMvcWithOtherCaller = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(filterWithOtherCaller, "/internal/v1/*")
                .build();

        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String bodyHash = HmacSignatureUtil.hashBody(null);
        String signature = HmacSignatureUtil.sign(
                otherService, "GET", path(userId), "", bodyHash, nonce, timestamp, otherSecret);

        mockMvcWithOtherCaller.perform(get(path(userId))
                        .header(InternalCallHeaders.SERVICE, otherService)
                        .header(InternalCallHeaders.TIMESTAMP, String.valueOf(timestamp))
                        .header(InternalCallHeaders.NONCE, nonce)
                        .header(InternalCallHeaders.SIGNATURE, signature))
                .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(weakConceptQueryService);
    }
}
