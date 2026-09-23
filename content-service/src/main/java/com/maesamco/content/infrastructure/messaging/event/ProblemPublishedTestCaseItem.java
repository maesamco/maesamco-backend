package com.maesamco.content.infrastructure.messaging.event;

import com.maesamco.content.domain.entity.problem.ProblemVersionTestCaseItem;

import java.util.UUID;

/** ProblemPublished 이벤트에 포함되는 테스트케이스입니다. */
public record ProblemPublishedTestCaseItem(
        UUID testCaseId,
        boolean isPublic,
        String input,
        String expectedOutput,
        int displayOrder
) {

    public static ProblemPublishedTestCaseItem from(ProblemVersionTestCaseItem testCase) {
        return new ProblemPublishedTestCaseItem(
                testCase.testCaseId(),
                testCase.isPublic(),
                testCase.input(),
                testCase.expectedOutput(),
                testCase.displayOrder()
        );
    }
}