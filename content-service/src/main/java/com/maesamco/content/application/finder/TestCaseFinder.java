package com.maesamco.content.application.finder;

import com.maesamco.content.domain.entity.TestCase;

import java.util.List;
import java.util.UUID;

/** 테스트케이스 조회 Port */
public interface TestCaseFinder {

    /** 테스트케이스를 조회합니다. */
    TestCase getById(UUID testCaseId);

    /** 특정 문제에 대해 승인된 테스트케이스를 조회합니다. */
    List<TestCase> findApprovedTestCases(UUID problemId);
}