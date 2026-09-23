package com.maesamco.coaching.infrastructure.ai;

import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 로컬 개발 전용 대체 어댑터 — .env의 AI_MODEL_CHAT=google-genai일 때만 활성화된다.
 * 팀 컨벤션상 실제 벤더는 Claude(ClaudeModelAdapter)이며, 이건 크레딧 소모 없이 Facade를
 * 개발·확인해보기 위한 개인용 대체다. .env에서 AI_MODEL_CHAT을 지우거나 anthropic으로 두면
 * 기본값(Claude)이 유지된다.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
public class GeminiModelAdapter implements AiModelPort {

    private final GoogleGenAiChatModel chatModel;

    public GeminiModelAdapter(GoogleGenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * ClaudeModelAdapter.generate()와 동일한 이유(튜터님 피드백) — 두 어댑터는
     * spring.ai.model.chat 프로퍼티로 상호 배타적으로만 활성화되므로 같은 CircuitBreaker
     * 인스턴스("ai-model")를 공유해도 무방하다.
     */
    @Override
    @CircuitBreaker(name = "ai-model", fallbackMethod = "generateFallback")
    public AiModelResponse generate(String systemPrompt, String userPrompt) {
        // 호출(네트워크) 실패와 응답 파싱 버그를 구분한다(PR #70 리뷰) — ClaudeModelAdapter와
        // 동일한 이유로 catch를 chatModel.call() 하나에만 좁힌다.
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (RuntimeException e) {
            throw new AiModelCallException("Gemini 호출에 실패했습니다.", e);
        }

        // 이슈 #262/#280(용현님 리뷰) — max-output-tokens를 늘리는 것만으로는 재발을 막지
        // 못한다. finishReason을 확인하지 않으면, 출력 한도로 중간에 잘린 응답도 content만
        // 그대로 뽑아서 정상 성공 응답으로 반환해버린다(원래 힌트 잘림 버그의 진짜 원인).
        // MAX_TOKENS로 끝난 응답은 실패로 분류해서 Facade가 AI_GENERATION_FAILED로
        // 처리하게 한다 — 잘린 내용이 정상 Hint/설명/피드백으로 저장되는 것을 막는다.
        String finishReason = response.getResult().getMetadata().getFinishReason();
        if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
            throw new AiModelCallException(
                    "Gemini 응답이 출력 토큰 한도로 중간에 잘렸습니다(finishReason=MAX_TOKENS).",
                    new IllegalStateException("finishReason=" + finishReason));
        }

        String content = response.getResult().getOutput().getText();
        String modelName = response.getMetadata().getModel();
        Integer tokenUsage = response.getMetadata().getUsage() == null
                ? null
                : response.getMetadata().getUsage().getTotalTokens();
        return new AiModelResponse(content, modelName, tokenUsage);
    }

    /**
     * ClaudeModelAdapter.generateFallback()과 동일한 이유(재검증, PR #111) — 서킷이 열려
     * 호출 자체가 차단된 경우(CallNotPermittedException)만 AiModelCallException으로 감싼다.
     * 그 외 RuntimeException(응답 파싱 버그 등)은 그대로 다시 던져 500 안전망으로 보낸다.
     *
     * PR #182 리뷰(용현님 P2) — ClaudeModelAdapter.generateFallback()과 동일하게
     * CallNotPermittedException만 neverCalled()=true로 표시한다.
     */
    @SuppressWarnings("unused")
    AiModelResponse generateFallback(String systemPrompt, String userPrompt, Throwable t) {
        if (t instanceof AiModelCallException aiModelCallException) {
            throw aiModelCallException;
        }
        if (t instanceof CallNotPermittedException) {
            throw new AiModelCallException("Gemini 호출이 차단되었습니다(circuit open).", t, true);
        }
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new AiModelCallException("Gemini 호출 중 예기치 못한 오류가 발생했습니다.", t);
    }
}
