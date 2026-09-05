package com.maesamco.content.dailyquiz.application.generation;

import com.maesamco.content.aigeneration.application.AiGenerationMetadata;
import lombok.Getter;

@Getter
public class DailyQuizQuestionGenerationException extends RuntimeException {

    private final String conceptTag;
    private final AiGenerationMetadata generationMetadata;

    public DailyQuizQuestionGenerationException(String conceptTag,
                                                AiGenerationMetadata generationMetadata,
                                                Throwable cause) {
        super(conceptTag + "에 대한 Daily Quiz 문항 생성에 실패했습니다.", cause);
        this.conceptTag = conceptTag;
        this.generationMetadata = generationMetadata;
    }
}
