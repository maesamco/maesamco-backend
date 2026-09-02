package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.response.SuccessResponse;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
 */
@SpringBootTest(classes = {
        JudgeServiceAdapter.class,
        JudgeServiceAdapterTest.MinimalAutoConfig.class
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

    @Test
    void 서킷이_닫혀있으면_SUBMISSION_NOT_FOUND가_그대로_전파된다() {
        UUID submissionId = UUID.randomUUID();
        Request request = Request.create(HttpMethod.GET, "/internal/v1/submissions/" + submissionId,
                Collections.emptyMap(), null, StandardCharsets.UTF_8);
        when(feignClient.getSubmission(submissionId))
                .thenThrow(new FeignException.NotFound("not found", request, null, null));

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
}
