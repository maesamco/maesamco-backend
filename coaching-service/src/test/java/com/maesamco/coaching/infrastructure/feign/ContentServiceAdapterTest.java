package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.application.port.ProblemSnapshot;
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
 * JudgeServiceAdapterTest와 동일한 이유 — getProblem()의 예외 매핑뿐 아니라 그 위에
 * 붙인 @CircuitBreaker가 Spring AOP 프록시를 통해 실제로 개입하는지까지 검증한다.
 */
@SpringBootTest(classes = {
        ContentServiceAdapter.class,
        ContentServiceAdapterTest.MinimalAutoConfig.class
})
class ContentServiceAdapterTest {

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
    private ContentServiceAdapter contentServiceAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private ContentServiceFeignClient feignClient;

    @AfterEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("content-service").reset();
    }

    @Test
    void 빈은_실제로_AOP_프록시로_감싸져있다() {
        assertThat(AopUtils.isAopProxy(contentServiceAdapter)).isTrue();
    }

    @Test
    void 서킷이_닫혀있으면_PROBLEM_NOT_FOUND가_그대로_전파된다() {
        UUID problemId = UUID.randomUUID();
        Request request = Request.create(HttpMethod.GET, "/internal/v1/problems/" + problemId,
                Collections.emptyMap(), null, StandardCharsets.UTF_8);
        when(feignClient.getProblem(problemId))
                .thenThrow(new FeignException.NotFound("not found", request, null, null));

        assertThatThrownBy(() -> contentServiceAdapter.getProblem(problemId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);
    }

    @Test
    void 서킷을_강제로_열면_실제_호출_없이_폴백이_FEIGN_CLIENT_ERROR로_응답한다() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("content-service");
        circuitBreaker.transitionToForcedOpenState();

        UUID problemId = UUID.randomUUID();

        assertThatThrownBy(() -> contentServiceAdapter.getProblem(problemId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FEIGN_CLIENT_ERROR);

        // 서킷이 열려있었으니 feignClient는 실제로 호출되지 않았어야 한다
        verifyNoInteractions(feignClient);
    }

    @Test
    void 정상_조회는_실제로_매핑까지_끝까지_동작한다() {
        UUID problemId = UUID.randomUUID();
        ProblemDetailResponse data = new ProblemDetailResponse(
                problemId, "문제 지문...", List.of("반복문", "배열")
        );
        when(feignClient.getProblem(problemId))
                .thenReturn(new SuccessResponse<>(true, data));

        ProblemSnapshot snapshot = contentServiceAdapter.getProblem(problemId);

        assertThat(snapshot.problemId()).isEqualTo(problemId);
        assertThat(snapshot.description()).isEqualTo("문제 지문...");
        assertThat(snapshot.conceptTags()).containsExactly("반복문", "배열");
    }

    @Test
    void conceptTags가_빈_배열이어도_정상_매핑된다() {
        UUID problemId = UUID.randomUUID();
        ProblemDetailResponse data = new ProblemDetailResponse(problemId, "문제 지문...", List.of());
        when(feignClient.getProblem(problemId))
                .thenReturn(new SuccessResponse<>(true, data));

        ProblemSnapshot snapshot = contentServiceAdapter.getProblem(problemId);

        assertThat(snapshot.conceptTags()).isEmpty();
    }
}
