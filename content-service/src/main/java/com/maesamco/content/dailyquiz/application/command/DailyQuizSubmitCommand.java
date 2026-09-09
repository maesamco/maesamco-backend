package com.maesamco.content.dailyquiz.application.command;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;

import java.util.UUID;

/**
 * 인증된 사용자의 Daily Quiz 문항 제출에 필요한 입력
 */
public record DailyQuizSubmitCommand(
        UUID userId,
        UUID quizAttemptId,
        UUID questionVersionId,
        String response
) {

    private static final int MAX_RESPONSE_LENGTH = 200;

    public DailyQuizSubmitCommand {
        if (userId == null) {
            throw invalidInput("사용자 ID는 필수입니다.");
        }
        if (quizAttemptId == null) {
            throw invalidInput("퀴즈 세트 ID는 필수입니다.");
        }
        if (questionVersionId == null) {
            throw invalidInput("문항 버전 ID는 필수입니다.");
        }
        if (response == null || response.isBlank()) {
            throw invalidInput("답안은 필수입니다.");
        }
        if (response.length() > MAX_RESPONSE_LENGTH) {
            throw invalidInput("답안은 200자를 초과할 수 없습니다.");
        }
    }

    public static DailyQuizSubmitCommand from(
            UUID userId,
            UUID quizAttemptId,
            UUID questionVersionId,
            String response
    ) {
        return new DailyQuizSubmitCommand(
                userId,
                quizAttemptId,
                questionVersionId,
                response
        );
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
    }
}
