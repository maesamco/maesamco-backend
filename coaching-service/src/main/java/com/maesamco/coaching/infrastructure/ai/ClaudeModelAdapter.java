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
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();
            String modelName = response.getMetadata().getModel();
            Integer tokenUsage = response.getMetadata().getUsage() == null
                    ? null
                    : response.getMetadata().getUsage().getTotalTokens();
            return new AiModelResponse(content, modelName, tokenUsage);
        } catch (RuntimeException e) {
            throw new AiModelCallException("Claude 호출에 실패했습니다.", e);
        }
    }
}
