package com.maesamco.content.dailyquiz.infrastructure.ai;

import com.maesamco.content.aigeneration.application.AiGenerationMetadata;
import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerationException;
import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerator;
import com.maesamco.content.dailyquiz.application.generation.GeneratedDailyQuizQuestion;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SpringAiDailyQuizQuestionGenerator implements DailyQuizQuestionGenerator {

    private final ChatClient chatClient;
    private final String configuredModelName;

    public SpringAiDailyQuizQuestionGenerator(
            ChatClient.Builder builder,
            ChatModel chatModel
    ) {
        this.chatClient = builder.build();
        this.configuredModelName = configuredModelName(chatModel);
    }

    @Override
    public GeneratedDailyQuizQuestion generate(String conceptTag) {
        Instant calledAt = Instant.now();
        long startedAtNanos = System.nanoTime();
        String modelName = configuredModelName;
        Integer tokenUsage = null;

        try {
            ResponseEntity<ChatResponse, AiGeneratedDailyQuizQuestionResponse> responseEntity =
                    chatClient.prompt()
                            .system(DailyQuizQuestionPrompt.SYSTEM_PROMPT)
                            .user(DailyQuizQuestionPrompt.userPrompt(conceptTag))
                            .call()
                            .responseEntity(AiGeneratedDailyQuizQuestionResponse.class);

            ChatResponse chatResponse = responseEntity.response();
            modelName = responseModelName(chatResponse, configuredModelName);
            tokenUsage = totalTokenUsage(chatResponse);

            AiGeneratedDailyQuizQuestionResponse response = responseEntity.entity();
            if (response == null) {
                throw new IllegalStateException("AI 문항 생성 응답이 비어 있습니다.");
            }

            AiGeneratedDailyQuizQuestionValidator.validate(response);

            AiGenerationMetadata generationMetadata = metadata(
                    modelName,
                    calledAt,
                    startedAtNanos,
                    tokenUsage
            );

            return new GeneratedDailyQuizQuestion(
                    response.problemType(),
                    response.questionText(),
                    response.choices(),
                    response.answer(),
                    response.allowedAnswerVariants(),
                    List.of(conceptTag),
                    generationMetadata
            );
        } catch (RuntimeException exception) {
            AiGenerationMetadata generationMetadata = metadata(
                    modelName,
                    calledAt,
                    startedAtNanos,
                    tokenUsage
            );

            throw new DailyQuizQuestionGenerationException(
                    conceptTag,
                    generationMetadata,
                    exception
            );
        }
    }

    private static String configuredModelName(ChatModel chatModel) {
        ChatOptions options = chatModel.getOptions();
        if (options == null || options.getModel() == null || options.getModel().isBlank()) {
            return "unknown";
        }

        return options.getModel();
    }

    private static String responseModelName(
            ChatResponse response,
            String fallbackModelName
    ) {
        if (response == null || response.getMetadata() == null) {
            return fallbackModelName;
        }

        String responseModelName = response.getMetadata().getModel();
        return responseModelName == null || responseModelName.isBlank()
                ? fallbackModelName
                : responseModelName;
    }

    private static Integer totalTokenUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }

        Usage usage = response.getMetadata().getUsage();
        return usage == null ? null : usage.getTotalTokens();
    }

    private static AiGenerationMetadata metadata(
            String modelName,
            Instant calledAt,
            long startedAtNanos,
            Integer tokenUsage
    ) {
        return new AiGenerationMetadata(
                modelName,
                DailyQuizQuestionPrompt.VERSION,
                calledAt,
                elapsedMillis(startedAtNanos),
                tokenUsage
        );
    }

    private static int elapsedMillis(long startedAtNanos) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAtNanos
        );
        return Math.toIntExact(Math.min(elapsedMillis, Integer.MAX_VALUE));
    }
}
