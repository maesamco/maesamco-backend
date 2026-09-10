package com.maesamco.content.testcase.presentation.dto.response;

import com.maesamco.content.testcase.domain.entity.TestCase;
import com.maesamco.content.testcase.domain.enums.TestCaseStatus;
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