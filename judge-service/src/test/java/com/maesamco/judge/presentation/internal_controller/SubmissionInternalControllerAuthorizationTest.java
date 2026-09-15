package com.maesamco.judge.presentation.internal_controller;

import com.maesamco.judge.application.query_service.SubmissionQueryService;
import com.maesamco.judge.application.result.SubmissionGetResult;
import com.maesamco.judge.domain.entity.SubmissionResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.global.exception.GlobalExceptionHandler;
import com.maesamco.judge.global.security.hmac.HmacSignatureUtil;
import com.maesamco.judge.global.security.hmac.HmacVerificationFilter;
import com.maesamco.judge.global.security.hmac.InternalCallHeaders;
import com.maesamco.judge.global.security.hmac.InternalCallerAuthorizationInterceptor;
import com.maesamco.judge.global.security.hmac.InternalServiceKeyProperties;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code SubmissionInternalController}에 붙은
 * {@code @AllowedInternalCallers({"content-service", "coaching-service"})}가 실제로
 * 강제되는지 검증한다(이슈 #175 P2 리뷰 — coaching-service의 인터셉터 로직 자체는
 * 단위테스트로 커버되지만, judge-service의 Config가 이 인터셉터를 실제로 배선하는지는
 * 검증된 적이 없었음).
 *
 * 기존 {@code SubmissionInternalControllerTest}는 {@code @WebMvcTest}로 컨트롤러 하나만
 * 좁게 슬라이스 테스트하고 {@code addFilters = false}로 HMAC 필터 자체를 꺼두기 때문에,
 * 이 인가 배선을 검증하기엔 적합하지 않다 — 이 클래스는 coaching-service의
 * WeakConceptInternalControllerSecurityTest와 동일한 패턴(HmacVerificationFilter +
 * InternalCallerAuthorizationInterceptor를 실제로 명시적으로 등록한 standaloneSetup)으로
 * 별도로 둔다.
 */
class SubmissionInternalControllerAuthorizationTest {

    private static final String ALLOWED_CALLER_1 = "content-service";
    private static final String ALLOWED_CALLER_2 = "coaching-service";
    private static final String SECRET_1 = "test-secret-content-to-judge";
    private static final String SECRET_2 = "test-secret-coaching-to-judge";

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private MockMvc mockMvc;
    private SubmissionQueryService submissionQueryService;

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
        submissionQueryService = Mockito.mock(SubmissionQueryService.class);
        SubmissionInternalController controller = new SubmissionInternalController(submissionQueryService);

        InternalServiceKeyProperties keyProperties = new InternalServiceKeyProperties(Map.of(
                ALLOWED_CALLER_1, SECRET_1,
                ALLOWED_CALLER_2, SECRET_2,
                "some-other-service", "other-secret-key"
        ));
        HmacVerificationFilter hmacVerificationFilter = new HmacVerificationFilter(keyProperties, redisTemplate);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(hmacVerificationFilter, "/internal/v1/*")
                .addInterceptors(new InternalCallerAuthorizationInterceptor())
                .build();
    }

    private String path(UUID submissionId) {
        return "/internal/v1/submissions/" + submissionId;
    }

    private String[] validHeaders(String callerService, String secretKey, String method, String path) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String bodyHash = HmacSignatureUtil.hashBody(null);
        String signature = HmacSignatureUtil.sign(
                callerService, method, path, "", bodyHash, nonce, timestamp, secretKey);
        return new String[]{String.valueOf(timestamp), nonce, signature};
    }

    private void mockValidSubmission(UUID submissionId) {
        SubmissionGetResult result = new SubmissionGetResult(
                submissionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "code",
                SubmissionStatus.COMPLETED, SubmissionResult.WRONG, null,
                List.of(new SubmissionGetResult.FailedTestItem(true, "WRONG_ANSWER")),
                3
        );
        given(submissionQueryService.getSubmissionForInternal(any())).willReturn(result);
    }

    @Test
    @DisplayName("content-service의 유효한 서명 요청은 통과한다")
    void acceptsContentService() throws Exception {
        UUID submissionId = UUID.randomUUID();
        mockValidSubmission(submissionId);
        String[] headers = validHeaders(ALLOWED_CALLER_1, SECRET_1, "GET", path(submissionId));

        mockMvc.perform(get(path(submissionId))
                        .header(InternalCallHeaders.SERVICE, ALLOWED_CALLER_1)
                        .header(InternalCallHeaders.TIMESTAMP, headers[0])
                        .header(InternalCallHeaders.NONCE, headers[1])
                        .header(InternalCallHeaders.SIGNATURE, headers[2]))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("coaching-service의 유효한 서명 요청도 통과한다")
    void acceptsCoachingService() throws Exception {
        UUID submissionId = UUID.randomUUID();
        mockValidSubmission(submissionId);
        String[] headers = validHeaders(ALLOWED_CALLER_2, SECRET_2, "GET", path(submissionId));

        mockMvc.perform(get(path(submissionId))
                        .header(InternalCallHeaders.SERVICE, ALLOWED_CALLER_2)
                        .header(InternalCallHeaders.TIMESTAMP, headers[0])
                        .header(InternalCallHeaders.NONCE, headers[1])
                        .header(InternalCallHeaders.SIGNATURE, headers[2]))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("서명은 유효하지만 허용 목록에 없는 호출자는 403으로 거부된다")
    void rejectsDisallowedCallerDespiteValidSignature() throws Exception {
        UUID submissionId = UUID.randomUUID();
        String otherService = "some-other-service";
        String otherSecret = "other-secret-key";
        String[] headers = validHeaders(otherService, otherSecret, "GET", path(submissionId));

        mockMvc.perform(get(path(submissionId))
                        .header(InternalCallHeaders.SERVICE, otherService)
                        .header(InternalCallHeaders.TIMESTAMP, headers[0])
                        .header(InternalCallHeaders.NONCE, headers[1])
                        .header(InternalCallHeaders.SIGNATURE, headers[2]))
                .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(submissionQueryService);
    }
}