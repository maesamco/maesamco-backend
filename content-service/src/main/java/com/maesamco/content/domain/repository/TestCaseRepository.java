package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.TestCaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseRepository {

    TestCase save(TestCase testCase);

    Optional<TestCase> findById(UUID testCaseId);

    int findMaxTestCaseOrderByProblemIdAndIsPublic(UUID problemId, boolean isPublic);

    Page<TestCase> searchTestCases(UUID problemId, boolean isPublic, Pageable pageable);

    Page<TestCase> searchTestCasesAll(UUID problemId, Pageable pageable);

    List<TestCase> findAllByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
            UUID problemId,
            TestCaseStatus testCaseStatus
    );
}