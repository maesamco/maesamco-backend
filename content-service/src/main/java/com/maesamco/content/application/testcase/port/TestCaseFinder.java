package com.maesamco.content.application.testcase.port;

import com.maesamco.content.domain.testcase.entity.TestCase;

import java.util.UUID;

/** 테스트케이스 조회 Port */
public interface TestCaseFinder {

    /** 테스트케이스를 조회합니다. */
    TestCase getTestCase(UUID testCaseId);
}