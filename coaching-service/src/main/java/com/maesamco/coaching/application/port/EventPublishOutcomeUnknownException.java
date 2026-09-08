package com.maesamco.coaching.application.port;

/**
 * {@link EventPublisherPort#publish}가 실제 전달 여부를 확인하지 못한 상황(응답 대기 시간
 * 초과, 대기 중 인터럽트 등)에서 던진다.
 *
 * 어댑터가 명시적으로 발행 실패를 확인한 경우와 달리, 이 예외는 전송이 백그라운드에서 계속
 * 진행돼 실제로는 이미 전달됐을 가능성을 배제할 수 없다 — 호출부(Relay Facade)는 이 예외를
 * 일반 발행 실패와 구분해서, 재시도 상한을 두고 FAILED로 종료하면 안 되고 무한 재시도해야
 * 한다(PR #123 심층 재검토, 2026-09-09).
 */
public class EventPublishOutcomeUnknownException extends RuntimeException {

    public EventPublishOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
