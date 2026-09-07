package com.maesamco.content.testcase.domain.repository;

import com.maesamco.content.testcase.domain.entity.TestCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** 테스트케이스 검색 Repository */
public interface TestCaseSearchRepository {

    /** 특정 문제의 테스트케이스 목록을 조회합니다. */
    Page<TestCase> searchTestCases(UUID problemId, Pageable pageable);
}