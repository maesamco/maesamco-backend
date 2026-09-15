package com.maesamco.content.problem.presentation.controller;

import com.maesamco.content.application.problem.service.ProblemInternalService;
import com.maesamco.content.global.exception.GlobalExceptionHandler;
import com.maesamco.content.global.security.hmac.HmacSignatureUtil;
import com.maesamco.content.global.security.hmac.HmacVerificationFilter;
import com.maesamco.content.global.security.hmac.InternalCallHeaders;
import com.maesamco.content.global.security.hmac.InternalCallerAuthorizationInterceptor;
import com.maesamco.content.global.security.hmac.InternalServiceKeyProperties;
import com.maesamco.content.presentation.problem.controller.InternalProblemController;
import com.maesamco.content.presentation.problem.dto.response.InternalProblemResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code InternalProblemController}에 붙은 {@code @AllowedInternalCallers({"coaching-service"})}가
 * 실제로 강제되는지 검증한다(이슈 #185 P1 재리뷰 — 이 컨트롤러가 원래 이 PR의 diff에
 * 빠져 있었고, 컨트롤러 단위 테스트 자체가 없어서(서비스 레이어 테스트만 존재) 이
 * 공백이 지금까지 테스트로도 드러나지 않았음).
 *
 * judge-service의 SubmissionInternalControllerAuthorizationTest와 동일한 패턴 —
 * HmacVerificationFilter + InternalCallerAuthorizationInterceptor를 실제로 명시적으로
 * 등록한 standaloneSetup으로, 서명 검증부터 인가 체크까지 실제 배선을 그대로 태운다.
 */
class InternalProblemControllerAuthorizationTest {

    private static final String ALLOWED_CALLER = "coaching-service";
    private static final String ALLOWED_SECRET = "test-secret-coaching-to-content";
    private static final String OTHER_CALLER = "some-other-service";
    private static final String OTHER_SECRET = "other-secret-key";

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private MockMvc mockMvc;
    private ProblemInternalService problemInternalService;

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
        problemInternalService = Mockito.mock(ProblemInternalService.class);
        InternalProblemController controller = new InternalProblemController(problemInternalService);

        InternalServiceKeyProperties keyProperties = new InternalServiceKeyProperties(Map.of(
                ALLOWED_CALLER, ALLOWED_SECRET,
                OTHER_CALLER, OTHER_SECRET
        ));
        HmacVerificationFilter hmacVerificationFilter = new HmacVerificationFilter(keyProperties, redisTemplate);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(hmacVerificationFilter, "/internal/v1/*")
                .addInterceptors(new InternalCallerAuthorizationInterceptor())
                .build();
    }

    private String[] validHeaders(String callerService, String secretKey, String method, String path) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String bodyHash = HmacSignatureUtil.hashBody(null);
        String signature = HmacSignatureUtil.sign(
                callerService, method, path, "", bodyHash, nonce, timestamp, secretKey);
        return new String[]{String.valueOf(timestamp), nonce, signature};
    }

    @Test
    @DisplayName("coaching-service의 유효한 서명 요청은 통과한다")
    void acceptsCoachingService() throws Exception {
        UUID problemId = UUID.randomUUID();
        String path = "/internal/v1/problems/" + problemId;
        given(problemInternalService.getProblemMetaData(problemId))
                .willReturn(new InternalProblemResponse(problemId, "설명", List.of("배열")));
        String[] headers = validHeaders(ALLOWED_CALLER, ALLOWED_SECRET, "GET", path);

        mockMvc.perform(get(path)
                        .header(InternalCallHeaders.SERVICE, ALLOWED_CALLER)
                        .header(InternalCallHeaders.TIMESTAMP, headers[0])
                        .header(InternalCallHeaders.NONCE, headers[1])
                        .header(InternalCallHeaders.SIGNATURE, headers[2]))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("서명은 유효하지만 coaching-service가 아닌 다른 호출자는 403으로 거부된다")
    void rejectsDisallowedCallerDespiteValidSignature() throws Exception {
        UUID problemId = UUID.randomUUID();
        String path = "/internal/v1/problems/" + problemId;
        String[] headers = validHeaders(OTHER_CALLER, OTHER_SECRET, "GET", path);

        mockMvc.perform(get(path)
                        .header(InternalCallHeaders.SERVICE, OTHER_CALLER)
                        .header(InternalCallHeaders.TIMESTAMP, headers[0])
                        .header(InternalCallHeaders.NONCE, headers[1])
                        .header(InternalCallHeaders.SIGNATURE, headers[2]))
                .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(problemInternalService);
    }

    @Test
    @DisplayName("problem-versions 엔드포인트도 동일하게 적용된다(클래스 레벨 애노테이션)")
    void appliesSameRestrictionToProblemVersionEndpoint() throws Exception {
        UUID problemVersionId = UUID.randomUUID();
        String path = "/internal/v1/problem-versions/" + problemVersionId;
        String[] headers = validHeaders(OTHER_CALLER, OTHER_SECRET, "GET", path);

        mockMvc.perform(get(path)
                        .header(InternalCallHeaders.SERVICE, OTHER_CALLER)
                        .header(InternalCallHeaders.TIMESTAMP, headers[0])
                        .header(InternalCallHeaders.NONCE, headers[1])
                        .header(InternalCallHeaders.SIGNATURE, headers[2]))
                .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(problemInternalService);
    }
}