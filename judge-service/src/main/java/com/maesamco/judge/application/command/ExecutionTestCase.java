package com.maesamco.judge.application.command;

import java.util.UUID;

/**
 * Judge Worker 실행 경로에서 쓰는 테스트케이스 값 객체.
 * ProblemPublished 이벤트 수신 시점(ProblemExecutionSpecSaveCommand.from())에서
 * 인프라 DTO(ProblemPublishedEvent.TestCaseItem)를 이 타입으로 변환해두기 때문에,
 * 이후 Application 계층(JudgeExecutionFacade, JudgeExecutionPersistenceService)은
 * Kafka 이벤트 계약을 몰라도 됩니다.
 */
public record ExecutionTestCase(
        UUID testCaseId,
        boolean isPublic,
        String input,
        String expectedOutput,
        int displayOrder
) {
}