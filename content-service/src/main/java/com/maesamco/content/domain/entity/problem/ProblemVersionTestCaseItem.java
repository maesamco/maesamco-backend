package com.maesamco.content.domain.entity.problem;

import com.maesamco.content.domain.entity.TestCase;

import java.util.UUID;

/** 문제 발행 버전에 포함되는 테스트케이스 한 건에 대한 스냅샷입니다. */
public record ProblemVersionTestCaseItem(
        UUID testCaseId,
        boolean isPublic,
        String input,
        String expectedOutput,
        int displayOrder
) {

    public static ProblemVersionTestCaseItem from(TestCase testCase) {
        return new ProblemVersionTestCaseItem(
                testCase.getId(),
                Boolean.TRUE.equals(testCase.getIsPublic()),
                testCase.getInput(),
                testCase.getExpectedOutput(),
                testCase.getTestCaseOrder()
        );
    }
}