package com.maesamco.coaching.infrastructure.ai;

import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
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
            throw new AiModelCallException("Gemini 호출에 실패했습니다.", e);
        }
    }
}
