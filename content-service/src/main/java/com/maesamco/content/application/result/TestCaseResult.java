package com.maesamco.content.application.result;

import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.TestCaseStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 테스트케이스 조회·생성·수정 결과입니다. Presentation의 응답 DTO와 분리된 Application 전용 타입입니다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TestCaseResult {

    private final UUID id;
    private final UUID problemId;
    private final String input;
    private final String expectedOutput;
    private final Boolean isPublic;
    private final Integer testCaseOrder;
    private final TestCaseStatus testCaseStatus;

    public static TestCaseResult from(TestCase testCase) {
        return new TestCaseResult(
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
