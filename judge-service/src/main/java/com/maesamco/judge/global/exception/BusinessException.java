package com.maesamco.judge.global.exception;

import lombok.Getter;

/**
 * 도메인 로직에서 의도적으로 던지는 모든 예외는 이 클래스 하나로 통일한다.
 * 서비스별 예외 클래스를 별도로 만들지 않는다(팀 컨벤션 13절).
 *
 * ⚠️ 로그 심각도는 ErrorCode가 아니라 이 인스턴스 자체에 싣는다(리뷰로 발견된 문제 —
 * PR #128 참고). 같은 ErrorCode(=같은 HTTP 응답)를 두 개의 서로 다른 상황이 공유할
 * 수 있기 때문이다. 예: AUTH_REFRESH_TOKEN_REUSED는 "정상적인 grace window 동시
 * 요청"과 "실제 재사용 탈취" 둘 다에서 던져지는데, HTTP 응답은 둘 다 동일해야 하지만
 * (공격자에게 어느 쪽으로 걸렸는지 정보를 흘리면 안 됨) 로그 심각도는 달라야 한다.
 * ErrorCode에 심각도를 고정하면 "같은 코드, 다른 상황"을 표현할 수 없어 원래
 * 문제(정상 동시 요청도 실제 탈취와 똑같이 WARN으로 찍힘)가 그대로 남는다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final LogSeverity logSeverity;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), LogSeverity.WARN);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, LogSeverity.WARN);
    }

    /**
     * 이 예외 인스턴스만의 로그 심각도를 명시적으로 지정한다.
     * 예: 정상적인 동시 요청처럼 "발생은 하지만 경보성은 아닌" 상황에서
     * new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_REUSED, msg, LogSeverity.DEBUG)
     */
    public BusinessException(ErrorCode errorCode, String message, LogSeverity logSeverity) {
        super(message);
        this.errorCode = errorCode;
        this.logSeverity = logSeverity;
    }

    public enum LogSeverity {
        DEBUG, WARN
    }
}