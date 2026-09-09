package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.global.config.CircuitBreakerIgnorableFailureConfig;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.response.SuccessResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * getSubmission()의 예외 매핑(Mockito 단위 테스트로도 충분)뿐 아니라, 그 위에 붙인
 * @CircuitBreaker가 Spring AOP 프록시를 통해 실제로 개입하는지까지 검증한다 — 이 부분은
 * HintGenerationFacadeTest처럼 JudgeServicePort(인터페이스)를 목으로 대체하는 테스트로는
 * 원천적으로 확인할 수 없다(목 객체는 프록시를 안 거치므로). 최소한의 자동 설정만 켠
 * 슬림 컨텍스트로 실제 빈을 띄워서 확인한다 — DB/Kafka/Redis/AI 벤더 자동 설정은 이
 * 테스트와 무관해서 제외했다(붙어 있으면 관련 커넥션 시도로 컨텍스트 로딩이 실패한다).
 *
 * CircuitBreakerIgnorableFailureConfig도 같이 등록한다(PR #127 심층 재검토, 2026-09-09)
 * — 실제 Spring AutoConfiguration이 judge-service 이름의 CircuitBreaker에 ignore
 * Predicate를 적용했는지, 그리고 그 Predicate가 SUBMISSION_NOT_FOUND만 무시하고
 * PROBLEM_NOT_FOUND(다른 서비스 코드)는 무시하지 않는지까지 실제 레지스트리로 검증한다.
 *
 * @TestPropertySource로 resilience4j.circuitbreaker.instances.judge-service.*를 직접
 * 지정하는 이유는 ContentServiceAdapterTest와 동일하다 — src/test/resources/
 * application.yml이 src/main/resources/application.yml을 클래스패스에서 완전히 가려서
 * (같은 경로, 병합 안 됨) main의 resilience4j 설정이 이 테스트엔 전혀 반영되지 않는다
 * (PR #127 심층 재검토, 2026-09-09 — 디버그로 실제 확인: 이름을 안 주면
 * CircuitBreakerRegistry가 즉석 기본 설정으로 만들어버려 customizer가 적용될 기회조차
 * 없다).
 */
@SpringBootTest(classes = {
        JudgeServiceAdapter.class,
        CircuitBreakerIgnorableFailureConfig.class,
        JudgeServiceAdapterTest.MinimalAutoConfig.class
})
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.judge-service.sliding-window-size=10",
        "resilience4j.circuitbreaker.instances.judge-service.minimum-number-of-calls=5"
})
class JudgeServiceAdapterTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            KafkaAutoConfiguration.class,
            DataRedisAutoConfiguration.class,
            DataRedisReactiveAutoConfiguration.class,
            AnthropicChatAutoConfiguration.class,
            GoogleGenAiChatAutoConfiguration.class
    })
    static class MinimalAutoConfig {
    }

    @Autowired
    private JudgeServiceAdapter judgeServiceAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private JudgeServiceFeignClient feignClient;

    @AfterEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("judge-service").reset();
    }

    @Test
    void 빈은_실제로_AOP_프록시로_감싸져있다() {
        assertThat(AopUtils.isAopProxy(judgeServiceAdapter)).isTrue();
    }

    /**
     * SUBMISSION_NOT_FOUND는 이제 JudgeServiceErrorDecoder(HTTP 계층)가 판단해서
     * BusinessException으로 던진다(PR #127 리뷰, 용현님 지적) — feignClient를 목으로
     * 대체하는 이 테스트는 디코더를 거치지 않으므로, 디코더가 이미 분류를 마친 뒤의
     * 상태(BusinessException 직접 던짐)를 그대로 재현해 어댑터/서킷브레이커 쪽 전파
     * 로직만 검증한다. 디코더 자체의 분류 로직은 JudgeServiceErrorDecoderTest가 검증한다.
     */
    @Test
    void 서킷이_닫혀있으면_SUBMISSION_NOT_FOUND가_그대로_전파된다() {
        UUID submissionId = UUID.randomUUID();
        when(feignClient.getSubmission(submissionId))
                .thenThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        assertThatThrownBy(() -> judgeServiceAdapter.getSubmission(submissionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND);
    }

    @Test
    void 서킷을_강제로_열면_실제_호출_없이_폴백이_FEIGN_CLIENT_ERROR로_응답한다() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("judge-service");
        circuitBreaker.transitionToForcedOpenState();

        UUID submissionId = UUID.randomUUID();

        assertThatThrownBy(() -> judgeServiceAdapter.getSubmission(submissionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FEIGN_CLIENT_ERROR);

        // 서킷이 열려있었으니 feignClient는 실제로 호출되지 않았어야 한다
        verifyNoInteractions(feignClient);
    }

    @Test
    void 정상_조회는_실제로_매핑까지_끝까지_동작한다() {
        UUID submissionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        SubmissionDetailResponse data = new SubmissionDetailResponse(
                submissionId, userId, problemId, "code", "WRONG",
                List.of(new SubmissionDetailResponse.FailedTestSummary(true, "WRONG_ANSWER")), 3
        );
        when(feignClient.getSubmission(submissionId))
                .thenReturn(new SuccessResponse<>(true, data));

        SubmissionSnapshot snapshot = judgeServiceAdapter.getSubmission(submissionId);

        assertThat(snapshot.submissionId()).isEqualTo(submissionId);
        assertThat(snapshot.userId()).isEqualTo(userId);
        assertThat(snapshot.isIncorrect()).isTrue();
    }

    @Test
    void SUBMISSION_NOT_FOUND는_실제_CircuitBreakerRegistry에서도_실패로_안_잡힌다() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("judge-service");
        long before = circuitBreaker.getMetrics().getNumberOfFailedCalls();

        UUID submissionId = UUID.randomUUID();
        when(feignClient.getSubmission(submissionId))
                .thenThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        assertThatThrownBy(() -> judgeServiceAdapter.getSubmission(submissionId)).isInstanceOf(BusinessException.class);

        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(before);
    }

    @Test
    void PROBLEM_NOT_FOUND는_judge_service_레지스트리에서는_실패로_잡힌다() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("judge-service");
        long before = circuitBreaker.getMetrics().getNumberOfFailedCalls();

        UUID submissionId = UUID.randomUUID();
        when(feignClient.getSubmission(submissionId))
                .thenThrow(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        assertThatThrownBy(() -> judgeServiceAdapter.getSubmission(submissionId)).isInstanceOf(BusinessException.class);

        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(before + 1);
    }

    @Test
    void FEIGN_CLIENT_ERROR는_실제_CircuitBreakerRegistry에서_실패로_잡힌다() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("judge-service");
        long before = circuitBreaker.getMetrics().getNumberOfFailedCalls();

        UUID submissionId = UUID.randomUUID();
        when(feignClient.getSubmission(submissionId))
                .thenThrow(new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR));

        assertThatThrownBy(() -> judgeServiceAdapter.getSubmission(submissionId)).isInstanceOf(BusinessException.class);

        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(before + 1);
    }
}
