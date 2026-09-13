package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.application.port.ProblemSnapshot;
import com.maesamco.coaching.global.config.CircuitBreakerIgnorableFailureConfig;
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
import org.springframework.test.context.TestPropertySource;
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
 *
 * CircuitBreakerIgnorableFailureConfig도 같이 등록한다(PR #127 심층 재검토,
 * 2026-09-09) — 이전엔 이 config가 빠진 채로 CircuitBreakerRegistry만 있어서, 실제
 * Spring AutoConfiguration이 content-service 이름의 CircuitBreaker에 ignore
 * Predicate를 적용했는지는 전혀 검증하지 못했다(Predicate 자체는
 * CircuitBreakerIgnorableFailureConfigTest에서 격리 검증하지만, 그게 실제로 이
 * CircuitBreaker 인스턴스에 배선됐는지는 별개 문제다).
 *
 * @TestPropertySource로 resilience4j.circuitbreaker.instances.content-service.*를
 * 직접 지정한다 — coaching-service/src/test/resources/application.yml이 테스트
 * 클래스패스에서 src/main/resources/application.yml을 완전히 가려서(같은
 * classpath:/application.yml 경로, Spring Boot는 첫 번째로 찾은 것만 로드하고 병합하지
 * 않음), main의 resilience4j 설정이 이 테스트에 전혀 반영되지 않는다는 걸 실제로
 * 확인했다(디버그 로그로 sliding-window-size가 기본값 100으로 나옴 — PR #127 심층
 * 재검토, 2026-09-09). 이름이 properties에 하나도 없으면 CircuitBreakerRegistry가
 * "content-service"를 즉석 기본 설정으로 만들어버려서 customizer 자체가 적용될 기회가
 * 없다 — 최소한 이 이름을 instances 맵에 등록시켜야 customizer가 실제로 붙는다.
 */
@SpringBootTest(classes = {
        ContentServiceAdapter.class,
        CircuitBreakerIgnorableFailureConfig.class,
        ContentServiceAdapterTest.MinimalAutoConfig.class
})
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.content-service.sliding-window-size=10",
        "resilience4j.circuitbreaker.instances.content-service.minimum-number-of-calls=5"
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

    /**
     * 404 자체는 이제 ContentServiceErrorDecoder(단위 테스트로 별도 검증)가 Feign
     * 계층에서 이미 BusinessException으로 변환해서 던진다 — 이 SpringBootTest는
     * ContentServiceFeignClient를 인터페이스 레벨에서 Mock으로 대체하기 때문에 그
     * 디코더를 실제로 거치지 않는다(PR #127 심층 재검토, 2026-09-09). 그래서 여기서는
     * 디코더가 이미 분류를 마쳤다고 가정하고, 그 결과(BusinessException)가 @CircuitBreaker
     * AOP를 그대로 통과해서 전파되는지만 검증한다 — 디코더의 판단 로직 자체는
     * ContentServiceErrorDecoderTest가 담당한다.
     */
    @Test
    void 서킷이_닫혀있으면_디코더가_분류한_PROBLEM_NOT_FOUND가_그대로_전파된다() {
        UUID problemId = UUID.randomUUID();
        when(feignClient.getProblem(problemId))
                .thenThrow(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        assertThatThrownBy(() -> contentServiceAdapter.getProblem(problemId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);
    }

    @Test
    void 디코더가_구분한_통신_계약_실패는_FEIGN_CLIENT_ERROR로_전파된다() {
        UUID problemId = UUID.randomUUID();
        when(feignClient.getProblem(problemId))
                .thenThrow(new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR));

        assertThatThrownBy(() -> contentServiceAdapter.getProblem(problemId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FEIGN_CLIENT_ERROR);
    }

    @Test
    void 디코더를_거치지_않은_일반_FeignException은_FEIGN_CLIENT_ERROR로_변환된다() {
        UUID problemId = UUID.randomUUID();
        Request request = Request.create(HttpMethod.GET, "/internal/v1/problems/" + problemId,
                Collections.emptyMap(), null, StandardCharsets.UTF_8);
        when(feignClient.getProblem(problemId))
                .thenThrow(new FeignException.InternalServerError("boom", request, null, null));

        assertThatThrownBy(() -> contentServiceAdapter.getProblem(problemId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FEIGN_CLIENT_ERROR);
    }

    @Test
    void PROBLEM_NOT_FOUND는_실제_CircuitBreakerRegistry에서도_실패로_안_잡힌다() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("content-service");
        long before = circuitBreaker.getMetrics().getNumberOfFailedCalls();

        UUID problemId = UUID.randomUUID();
        when(feignClient.getProblem(problemId))
                .thenThrow(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        assertThatThrownBy(() -> contentServiceAdapter.getProblem(problemId)).isInstanceOf(BusinessException.class);

        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(before);
    }

    @Test
    void FEIGN_CLIENT_ERROR는_실제_CircuitBreakerRegistry에서_실패로_잡힌다() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("content-service");
        long before = circuitBreaker.getMetrics().getNumberOfFailedCalls();

        UUID problemId = UUID.randomUUID();
        when(feignClient.getProblem(problemId))
                .thenThrow(new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR));

        assertThatThrownBy(() -> contentServiceAdapter.getProblem(problemId)).isInstanceOf(BusinessException.class);

        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(before + 1);
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
