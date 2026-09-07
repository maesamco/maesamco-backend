package com.maesamco.content.testcase.domain.repository;

import com.maesamco.content.testcase.domain.entity.TestCase;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 테스트케이스 Repository */
public interface TestCaseRepository extends JpaRepository<TestCase, UUID>, TestCaseSearchRepository {

    /** 삭제되지 않은 테스트케이스를 조회합니다. */
    @NonNull Optional<TestCase> findById(@NonNull UUID id);

    /** 특정 문제의 삭제되지 않은 테스트케이스 목록을 순서대로 조회합니다. */
    List<TestCase> findAllByProblemIdOrderByTestCaseOrderAsc(UUID problemId);
}