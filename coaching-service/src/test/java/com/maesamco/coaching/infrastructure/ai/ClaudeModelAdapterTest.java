package com.maesamco.coaching.infrastructure.ai;

import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * JudgeServiceAdapterTest와 동일한 이유(튜터님 피드백) — generate()에 붙인
 * @CircuitBreaker가 Spring AOP 프록시를 통해 실제로 개입하는지까지 검증한다. Mockito 목
 * 객체(AiModelPort)로 대체하는 Facade 테스트로는 원천적으로 확인할 수 없는 부분이다.
 */
@SpringBootTest(classes = {
        ClaudeModelAdapter.class,
        ClaudeModelAdapterTest.MinimalAutoConfig.class
})
class ClaudeModelAdapterTest {

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
    private ClaudeModelAdapter claudeModelAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private AnthropicChatModel chatModel;

    @AfterEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("ai-model").reset();
    }

    @Test
    void 빈은_실제로_AOP_프록시로_감싸져있다() {
        assertThat(AopUtils.isAopProxy(claudeModelAdapter)).isTrue();
    }

    @Test
    void 서킷이_닫혀있으면_호출_실패가_AiModelCallException으로_그대로_전파된다() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("네트워크 오류"));

        assertThatThrownBy(() -> claudeModelAdapter.generate("system", "user"))
                .isInstanceOf(AiModelCallException.class);
    }

    @Test
    void 서킷을_강제로_열면_실제_호출_없이_폴백이_AiModelCallException으로_응답한다() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("ai-model");
        circuitBreaker.transitionToForcedOpenState();

        assertThatThrownBy(() -> claudeModelAdapter.generate("system", "user"))
                .isInstanceOf(AiModelCallException.class);

        // 서킷이 열려있었으니 chatModel은 실제로 호출되지 않았어야 한다
        verifyNoInteractions(chatModel);
    }

    @Test
    void 응답_매핑_단계의_버그는_서킷차단으로_오분류되지_않고_그대로_전파된다() {
        // 재검증(PR #111) — chatModel.call() 자체는 성공(null 반환)했지만 그 이후
        // response.getResult() 단계에서 NPE가 나는 상황을 재현한다. CircuitBreaker의
        // fallbackMethod는 서킷 상태와 무관하게 generate()가 던지는 모든 예외를 가로채므로,
        // 이 NPE도 generateFallback()으로 들어간다 — CallNotPermittedException이 아니므로
        // AiModelCallException(circuitOpen=true)으로 잘못 감싸지 않고 원본 그대로
        // 다시 던져져야 한다(GlobalExceptionHandler의 500 안전망으로 가야 정상).
        when(chatModel.call(any(Prompt.class))).thenReturn(null);

        assertThatThrownBy(() -> claudeModelAdapter.generate("system", "user"))
                .isInstanceOf(NullPointerException.class)
                .isNotInstanceOf(AiModelCallException.class);
    }

    @Test
    void 정상_호출은_실제로_매핑까지_끝까지_동작한다() {
        Generation generation = new Generation(
                org.springframework.ai.chat.messages.AssistantMessage.builder()
                        .content("응답 텍스트")
                        .build()
        );
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("claude-sonnet-5")
                .usage(new DefaultUsage(10, 20))
                .build();
        ChatResponse response = ChatResponse.builder()
                .generations(java.util.List.of(generation))
                .metadata(metadata)
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        AiModelResponse result = claudeModelAdapter.generate("system", "user");

        assertThat(result.content()).isEqualTo("응답 텍스트");
        assertThat(result.modelName()).isEqualTo("claude-sonnet-5");
        assertThat(result.tokenUsage()).isEqualTo(30);
    }
}
