package com.maesamco.content.infrastructure.dailyquiz.ai;

import com.maesamco.content.application.aigeneration.AiGenerationMetadata;
import com.maesamco.content.application.dailyquiz.generation.DailyQuizQuestionGenerationException;
import com.maesamco.content.application.dailyquiz.generation.DailyQuizQuestionGenerator;
import com.maesamco.content.application.dailyquiz.generation.GeneratedDailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class SpringAiDailyQuizQuestionGenerator implements DailyQuizQuestionGenerator {

    private static final int MAX_VALIDATION_ATTEMPTS = 2;

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
    public GeneratedDailyQuizQuestion generate(QuestionSlot questionSlot) {
        String conceptTag = questionSlot.conceptTag();
        Instant calledAt = Instant.now();
        long startedAtNanos = System.nanoTime();
        String modelName = configuredModelName;
        Integer tokenUsage = null;

        try {
            AiGeneratedDailyQuizQuestionResponse response = null;
            String validationFailureReason = null;

            for (int attempt = 1; attempt <= MAX_VALIDATION_ATTEMPTS; attempt++) {
                String userPrompt = validationFailureReason == null
                        ? DailyQuizQuestionPrompt.userPrompt(questionSlot)
                        : DailyQuizQuestionPrompt.retryUserPrompt(questionSlot, validationFailureReason);

                ResponseEntity<ChatResponse, AiGeneratedDailyQuizQuestionResponse> responseEntity =
                        chatClient.prompt()
                                .system(DailyQuizQuestionPrompt.SYSTEM_PROMPT)
                                .user(userPrompt)
                                .call()
                                .responseEntity(AiGeneratedDailyQuizQuestionResponse.class);

                ChatResponse chatResponse = responseEntity.response();
                modelName = responseModelName(chatResponse, modelName);
                tokenUsage = addTokenUsage(tokenUsage, totalTokenUsage(chatResponse));
                response = responseEntity.entity();

                try {
                    validateResponse(response, questionSlot);
                    break;
                } catch (IllegalArgumentException exception) {
                    if (attempt == MAX_VALIDATION_ATTEMPTS) {
                        throw exception;
                    }

                    validationFailureReason = exception.getMessage();
                    log.warn(
                            "AI 생성 Daily Quiz 문항 검증 실패로 1회 재생성합니다. "
                                    + "conceptTag={}, problemType={}, reason={}",
                            questionSlot.conceptTag(),
                            questionSlot.problemType(),
                            validationFailureReason
                    );
                }
            }

            if (response == null) {
                throw new IllegalArgumentException("AI 문항 생성 응답이 비어 있습니다.");
            }

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

    private static Integer addTokenUsage(Integer accumulatedTokenUsage, Integer currentTokenUsage) {
        if (accumulatedTokenUsage == null) {
            return currentTokenUsage;
        }
        if (currentTokenUsage == null) {
            return accumulatedTokenUsage;
        }

        long totalTokenUsage = (long) accumulatedTokenUsage + currentTokenUsage;
        return Math.toIntExact(Math.min(totalTokenUsage, Integer.MAX_VALUE));
    }

    private static void validateResponse(
            AiGeneratedDailyQuizQuestionResponse response,
            QuestionSlot questionSlot
    ) {
        if (response == null) {
            throw new IllegalArgumentException("AI 문항 생성 응답이 비어 있습니다.");
        }

        AiGeneratedDailyQuizQuestionValidator.validate(response, questionSlot.problemType());
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
