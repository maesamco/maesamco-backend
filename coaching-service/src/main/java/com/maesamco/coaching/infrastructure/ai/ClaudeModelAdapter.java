package com.maesamco.coaching.infrastructure.ai;

import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 팀 컨벤션상 실제 벤더(Claude/Anthropic) 어댑터 — 힌트·역질문·피드백 생성의 기본 구현체.
 * AnthropicChatModel 빈도 정확히 같은 조건(spring.ai.model.chat=anthropic, matchIfMissing=true)으로
 * 생성되므로 이 어댑터도 같은 프로퍼티로 게이트한다 — .env의 AI_MODEL_CHAT 값 하나로 벤더가 갈린다.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "anthropic", matchIfMissing = true)
public class ClaudeModelAdapter implements AiModelPort {

    private final AnthropicChatModel chatModel;

    public ClaudeModelAdapter(AnthropicChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 튜터님 피드백 — 외부 LLM 장애가 내부 장애로 전파되지 않도록 CircuitBreaker를 적용한다.
     * Feign이 아니라서 팀 컨벤션 2절("모든 FeignAdapter 메서드에 CircuitBreaker 적용")의
     * 문자 그대로의 적용 대상은 아니지만, "외부 호출 장애 격리"라는 같은 원칙을 적용한다.
     */
    @Override
    @CircuitBreaker(name = "ai-model", fallbackMethod = "generateFallback")
    public AiModelResponse generate(String systemPrompt, String userPrompt) {
        // 호출(네트워크) 실패와 응답 파싱 버그를 구분한다(PR #70 리뷰) — catch를 chatModel.call()
        // 하나에만 좁혀서, 파싱 단계의 NPE 등 우리 코드 버그까지 "Claude 호출 실패"(503)로
        // 뭉뚱그려지지 않게 한다. 파싱 버그는 GlobalExceptionHandler의 500 안전망으로 간다.
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (RuntimeException e) {
            throw new AiModelCallException("Claude 호출에 실패했습니다.", e);
        }
        String content = response.getResult().getOutput().getText();
        String modelName = response.getMetadata().getModel();
        Integer tokenUsage = response.getMetadata().getUsage() == null
                ? null
                : response.getMetadata().getUsage().getTotalTokens();
        return new AiModelResponse(content, modelName, tokenUsage);
    }

    /**
     * JudgeServiceAdapter.getSubmissionFallback()과 동일한 이유 — generate()가 이미
     * AiModelCallException으로 분류해서 던진 경우(서킷이 닫혀 있어 실제로 호출까지 됐던
     * 경우)는 그대로 다시 던진다. 서킷이 열려 호출 자체가 차단된 경우(CallNotPermittedException)만
     * 새로 AiModelCallException(circuitOpen=true)으로 감싼다.
     *
     * 재검증(PR #111) — CircuitBreaker의 fallbackMethod는 서킷 상태와 무관하게 generate()가
     * 던지는 모든 예외를 가로챈다. 그래서 generate()의 응답 파싱 단계(위 주석, PR #70)에서
     * NPE 등 우리 코드 버그가 나도 여기로 들어오는데, CallNotPermittedException이 아닌
     * RuntimeException은 여기서 삼키지 않고 그대로 다시 던져야 "파싱 버그는 500 안전망으로
     * 간다"는 PR #70의 원래 의도가 지켜진다 — circuitOpen=true로 잘못 표시하면
     * FeedbackGenerationFacade가 실제 버그로 인한 실패를 SKIPPED로 기록해 재시도 예산
     * 계산에서 숨겨버린다.
     */
    @SuppressWarnings("unused")
    AiModelResponse generateFallback(String systemPrompt, String userPrompt, Throwable t) {
        if (t instanceof AiModelCallException aiModelCallException) {
            throw aiModelCallException;
        }
        if (t instanceof CallNotPermittedException) {
            throw new AiModelCallException("Claude 호출이 차단되었습니다(circuit open).", t, true);
        }
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new AiModelCallException("Claude 호출 중 예기치 못한 오류가 발생했습니다.", t);
    }
}
