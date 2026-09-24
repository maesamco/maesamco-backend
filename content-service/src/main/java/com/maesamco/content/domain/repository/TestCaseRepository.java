package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.domain.entity.TestCaseStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseRepository {

    TestCase save(TestCase testCase);

    List<TestCase> saveAll(List<TestCase> testCases);

    Optional<TestCase> findById(UUID testCaseId);

    void delete(TestCase testCase);

    void flush();

    int findMaxTestCaseOrderByProblemIdAndIsPublic(UUID problemId, boolean isPublic);

    PageResult<TestCase> searchTestCases(UUID problemId, boolean isPublic, PageQuery pageQuery);

    PageResult<TestCase> searchTestCasesAll(UUID problemId, PageQuery pageQuery);

    List<TestCase> findAllByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
            UUID problemId,
            TestCaseStatus testCaseStatus
    );
}