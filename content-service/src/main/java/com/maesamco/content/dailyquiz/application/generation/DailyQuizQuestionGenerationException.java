package com.maesamco.content.dailyquiz.application.generation;

import lombok.Getter;

@Getter
public class DailyQuizQuestionGenerationException extends RuntimeException {

    private final String conceptTag;

    public DailyQuizQuestionGenerationException(String conceptTag, Throwable cause) {
        super(conceptTag + "에 대한 Daily Quiz 문항 생성에 실패했습니다.", cause);
        this.conceptTag = conceptTag;
    }
}
