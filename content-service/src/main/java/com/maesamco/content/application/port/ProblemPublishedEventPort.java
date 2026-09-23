package com.maesamco.content.application.port;

/**
 * 문제 발행 이벤트 기록을 Application 계층에서 요청하기 위한 포트입니다.
 * 이벤트 변환, 직렬화 및 Outbox 저장 방식은 Infrastructure 계층에서 결정합니다.
 */
public interface ProblemPublishedEventPort {

    void record(ProblemPublishedEventData eventData);
}