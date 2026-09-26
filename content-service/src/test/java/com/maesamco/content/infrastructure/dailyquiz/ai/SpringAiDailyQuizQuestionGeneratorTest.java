package com.maesamco.content.infrastructure.dailyquiz.ai;

import com.maesamco.content.application.dailyquiz.generation.DailyQuizQuestionGenerationException;
import com.maesamco.content.application.dailyquiz.generation.GeneratedDailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiDailyQuizQuestionGeneratorTest {

    private static final QuestionSlot FILL_IN_BLANK_SLOT = new QuestionSlot(
            "반복문",
            DailyQuizProblemType.FILL_IN_BLANK
    );

    @Mock
    private ChatModel chatModel;

    private ChatOptions chatOptions;

    private SpringAiDailyQuizQuestionGenerator generator;

    @BeforeEach
    void setUp() {
        chatOptions = ChatOptions.builder()
                .model("configured-model")
                .build();
        when(chatModel.getOptions()).thenReturn(chatOptions);
        generator = new SpringAiDailyQuizQuestionGenerator(
                ChatClient.builder(chatModel),
                chatModel
        );
    }

    @Test
    void 첫_응답이_유효하면_한_번만_호출한다() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response(validFillInBlankJson(), 30));

        GeneratedDailyQuizQuestion result = generator.generate(FILL_IN_BLANK_SLOT);

        assertThat(result.problemType()).isEqualTo(DailyQuizProblemType.FILL_IN_BLANK);
        assertThat(result.questionText()).contains("___");
        assertThat(result.generationMetadata().tokenUsage()).isEqualTo(30);
        assertThat(result.generationMetadata().promptVersion()).isEqualTo("v4");
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void 첫_응답의_형식이_잘못되면_실패_사유를_포함해_한_번_재생성한다() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response(invalidFillInBlankJson(), 20))
                .thenReturn(response(validFillInBlankJson(), 30));

        GeneratedDailyQuizQuestion result = generator.generate(FILL_IN_BLANK_SLOT);

        assertThat(result.problemType()).isEqualTo(DailyQuizProblemType.FILL_IN_BLANK);
        assertThat(result.questionText()).contains("___");
        assertThat(result.generationMetadata().tokenUsage()).isEqualTo(50);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1).getUserMessage().getText())
                .contains("이전 응답의 검증 실패 사유")
                .contains("___가 정확히 한 번 있어야 합니다.");
    }

    @Test
    void 문제_유형이_다른_응답도_재생성한다() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response(shortAnswerJson(), 20))
                .thenReturn(response(validFillInBlankJson(), 30));

        GeneratedDailyQuizQuestion result = generator.generate(FILL_IN_BLANK_SLOT);

        assertThat(result.problemType()).isEqualTo(DailyQuizProblemType.FILL_IN_BLANK);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void 두_번째_응답도_검증에_실패하면_최종_실패로_처리한다() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response(invalidFillInBlankJson(), 20))
                .thenReturn(response(invalidFillInBlankJson(), 30));

        DailyQuizQuestionGenerationException exception = catchThrowableOfType(
                DailyQuizQuestionGenerationException.class,
                () -> generator.generate(FILL_IN_BLANK_SLOT)
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("___가 정확히 한 번 있어야 합니다.");
        assertThat(exception.getGenerationMetadata().tokenUsage()).isEqualTo(50);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void 빈_응답도_한_번_재생성한다() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("null", 20))
                .thenReturn(response(validFillInBlankJson(), 30));

        GeneratedDailyQuizQuestion result = generator.generate(FILL_IN_BLANK_SLOT);

        assertThat(result.problemType()).isEqualTo(DailyQuizProblemType.FILL_IN_BLANK);
        assertThat(result.generationMetadata().tokenUsage()).isEqualTo(50);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    private ChatResponse response(String json, int totalTokens) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("response-model")
                .usage(new DefaultUsage(totalTokens, 0, totalTokens))
                .build();
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(json))),
                metadata
        );
    }

    private String validFillInBlankJson() {
        return """
                {
                  "problemType": "FILL_IN_BLANK",
                  "questionText": "Java의 조건 반복문 키워드는 ___입니다.",
                  "choices": null,
                  "answer": "while",
                  "allowedAnswerVariants": null
                }
                """;
    }

    private String invalidFillInBlankJson() {
        return """
                {
                  "problemType": "FILL_IN_BLANK",
                  "questionText": "Java의 조건 반복문 키워드를 작성하세요.",
                  "choices": null,
                  "answer": "while",
                  "allowedAnswerVariants": null
                }
                """;
    }

    private String shortAnswerJson() {
        return """
                {
                  "problemType": "SHORT_ANSWER",
                  "questionText": "Java의 조건 반복문 키워드를 작성하세요.",
                  "choices": null,
                  "answer": "while",
                  "allowedAnswerVariants": null
                }
                """;
    }
}
