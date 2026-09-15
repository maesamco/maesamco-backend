package com.maesamco.content.presentation.response;

import com.maesamco.content.domain.testcase.entity.TestCase;
import com.maesamco.content.domain.testcase.enums.TestCaseStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 테스트케이스 생성 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TestCaseCreateResponse {

    private final UUID id;
    private final UUID problemId;
    private final TestCaseStatus testCaseStatus;

    public static TestCaseCreateResponse from(TestCase testCase) {
        return new TestCaseCreateResponse(
                testCase.getId(),
                testCase.getProblemId(),
                testCase.getTestCaseStatus()
        );
    }
}