package com.maesamco.content.dailyquiz.application.exception;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 특정 사용자의 데이터 문제로 Daily Quiz 생성을 계속할 수 없을 때 사용하는 예외입니다.
 * 배치는 이 예외만 사용자 단위로 격리하고 다음 사용자를 처리합니다.
 */
public class DailyQuizUserProcessingException extends RuntimeException {

    public DailyQuizUserProcessingException(
            UUID userId,
            LocalDate attemptDate,
            Throwable cause
    ) {
        super(
                "Daily Quiz 사용자 데이터를 처리할 수 없습니다. userId=%s, attemptDate=%s"
                        .formatted(userId, attemptDate),
                cause
        );
    }
}
