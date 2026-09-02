package com.maesamco.coaching.infrastructure.ai;

import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
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

    @Override
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
}
