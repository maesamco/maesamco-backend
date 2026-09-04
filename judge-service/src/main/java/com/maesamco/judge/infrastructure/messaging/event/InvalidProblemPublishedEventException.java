package com.maesamco.judge.infrastructure.messaging.event;

/** ProblemPublished 이벤트가 계약을 어긴 경우(필수 필드 누락 등) — 재시도해도 해결 안 되므로 즉시 DLT 대상. */
public class InvalidProblemPublishedEventException extends RuntimeException {
    public InvalidProblemPublishedEventException(String message) {
        super(message);
    }
}
