package com.maesamco.content.application.dailyquiz.exception;

import java.time.LocalDate;

public class DailyQuizQuestionSupplyException extends RuntimeException {

    public DailyQuizQuestionSupplyException(LocalDate attemptDate, int affectedUsers) {
        super("Daily Quiz 최소 문항을 확보하지 못했습니다. attemptDate=" + attemptDate
                + ", affectedUsers=" + affectedUsers);
    }
}
