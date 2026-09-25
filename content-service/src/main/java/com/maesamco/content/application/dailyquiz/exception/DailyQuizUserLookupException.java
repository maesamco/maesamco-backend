package com.maesamco.content.application.dailyquiz.exception;

/**
 * 특정 사용자의 관심 개념 조회 응답만 처리할 수 없을 때 사용합니다.
 */
public class DailyQuizUserLookupException extends RuntimeException {

    public DailyQuizUserLookupException(String message) {
        super(message);
    }

    public DailyQuizUserLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
