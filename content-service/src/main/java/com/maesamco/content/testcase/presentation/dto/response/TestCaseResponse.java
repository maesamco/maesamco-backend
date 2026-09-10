package com.maesamco.content.testcase.presentation.dto.response;

import com.maesamco.content.testcase.domain.entity.TestCase;
import com.maesamco.content.testcase.domain.enums.TestCaseStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 테스트케이스 조회 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TestCaseResponse {

    private final UUID id;
    private final UUID problemId;
    private final String input;
    private final String expectedOutput;
    private final Boolean isPublic;
    private final Integer testCaseOrder;
    private final TestCaseStatus testCaseStatus;

    public static TestCaseResponse from(TestCase testCase) {
        return new TestCaseResponse(
                testCase.getId(),
                testCase.getProblemId(),
                testCase.getInput(),
                testCase.getExpectedOutput(),
                testCase.getIsPublic(),
                testCase.getTestCaseOrder(),
                testCase.getTestCaseStatus()
        );
    }
}