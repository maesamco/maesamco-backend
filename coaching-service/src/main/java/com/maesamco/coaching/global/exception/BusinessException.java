package com.maesamco.coaching.global.exception;

import lombok.Getter;

/**
 * 도메인 로직에서 의도적으로 던지는 모든 예외는 이 클래스 하나로 통일한다.
 * 서비스별 예외 클래스를 별도로 만들지 않는다(팀 컨벤션 13절).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
