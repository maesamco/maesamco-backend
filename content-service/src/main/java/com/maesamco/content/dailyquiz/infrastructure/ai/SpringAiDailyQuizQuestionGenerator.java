package com.maesamco.content.dailyquiz.infrastructure.ai;

import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerationException;
import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerator;
import com.maesamco.content.dailyquiz.application.generation.GeneratedDailyQuizQuestion;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiDailyQuizQuestionGenerator implements DailyQuizQuestionGenerator {

    private final ChatClient chatClient;

    public SpringAiDailyQuizQuestionGenerator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public GeneratedDailyQuizQuestion generate(String conceptTag) {
        AiGeneratedDailyQuizQuestionResponse response;

        try {
            response = chatClient.prompt()
                    .system(DailyQuizQuestionPrompt.SYSTEM_PROMPT)
                    .user(DailyQuizQuestionPrompt.userPrompt(conceptTag))
                    .call()
                    .entity(AiGeneratedDailyQuizQuestionResponse.class);

            if (response == null) {
                throw new IllegalStateException("AI 문항 생성 응답이 비어 있습니다.");
            }

            AiGeneratedDailyQuizQuestionValidator.validate(response);

        } catch (RuntimeException exception) {
            throw new DailyQuizQuestionGenerationException(conceptTag, exception);
        }

        return new GeneratedDailyQuizQuestion(
                response.problemType(),
                response.questionText(),
                response.choices(),
                response.answer(),
                response.allowedAnswerVariants(),
                List.of(conceptTag)
        );
    }
}
