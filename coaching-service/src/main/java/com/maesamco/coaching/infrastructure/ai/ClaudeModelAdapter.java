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
     *
     * TODO(#180): spring.ai.anthropic.chat.options.max-tokens를 명시적으로 설정 안 해도
     * Spring AI 기본값(AnthropicChatOptions.DEFAULT_MAX_TOKENS=4096)이 이미 적용되고
     * 있다(바이트코드로 확인, Anthropic API가 max_tokens를 필수 파라미터로 요구하기 때문
     * — "제한이 아예 없다"는 이슈 #150의 원래 서술은 부정확했음). 4096이 충분한지는
     * responseTimeMs/tokenUsage 계측 데이터로 실제 truncation 여부를 본 뒤 결정한다.
     */
    @Override
    @CircuitBreaker(name = "ai-model", fallbackMethod = "generateFallback")
    public AiModelResponse generate(String systemPrompt, String userPrompt) {
        // 호출(네트워크) 실패와 응답 파싱 버그를 구분한다(PR #70 리뷰) — catch를 chatModel.call()
        // 하나에만 좁혀서, 파싱 단계의 NPE 등 우리 코드 버그까지 "Claude 호출 실패"(503)로
        // 뭉뚱그려지지 않게 한다. 파싱 버그는 GlobalExceptionHandler의 500 안전망으로 간다.
        //
        // 이슈 #206(해결됨) — 이 e(Anthropic SDK 원본 예외)가 각 Facade의 log.warn(..., e)로
        // 그대로 로깅되는데, Anthropic SDK의 에러 메시지가 팀 컨벤션 14절이 금지하는 "사용자
        // 제출 코드 원문"을 담을 수 있는지는 Gemini/Claude 둘 다 실제 유료 API 키로 잘못된
        // 모델명을 유발해 실측 확인 완료 — 두 벤더 모두 cause 체인에 노출 없음.
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
     * 새로 AiModelCallException으로 감싼다.
     *
     * 재검증(PR #111) — CircuitBreaker의 fallbackMethod는 서킷 상태와 무관하게 generate()가
     * 던지는 모든 예외를 가로챈다. 그래서 generate()의 응답 파싱 단계(위 주석, PR #70)에서
     * NPE 등 우리 코드 버그가 나도 여기로 들어오는데, CallNotPermittedException이 아닌
     * RuntimeException은 여기서 삼키지 않고 그대로 다시 던져야 "파싱 버그는 500 안전망으로
     * 간다"는 PR #70의 원래 의도가 지켜진다.
     *
     * PR #182 리뷰(용현님 P2) — CallNotPermittedException(서킷오픈, chatModel.call() 자체가
     * 실행 안 됨)만 AiModelCallException.neverCalled()=true로 표시한다. generate()의
     * catch에서 이미 감싸진 경우는 그대로 재던지므로 neverCalled()=false를 그대로 유지한다.
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
