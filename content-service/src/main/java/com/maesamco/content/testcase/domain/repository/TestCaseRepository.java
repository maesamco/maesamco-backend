package com.maesamco.content.testcase.domain.repository;

import com.maesamco.content.testcase.domain.entity.TestCase;
import com.maesamco.content.testcase.domain.enums.TestCaseStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 테스트케이스 Repository */
public interface TestCaseRepository
        extends JpaRepository<TestCase, UUID>,
        TestCaseSearchRepository,
        TestCaseOrderRepository {

    /** 삭제되지 않은 테스트케이스를 조회합니다. */
    @NonNull
    Optional<TestCase> findById(@NonNull UUID id);

    /**
     * 문제 발행 시 사용할 테스트케이스를 조회합니다.
     *
     * <p>지정한 상태의 테스트케이스만 조회하며,
     * 공개 테스트케이스를 먼저 배치한 뒤
     * 그룹별 순서와 ID를 기준으로 정렬합니다.</p>
     */
    List<TestCase>
    findAllByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
            UUID problemId,
            TestCaseStatus testCaseStatus
    );
}
