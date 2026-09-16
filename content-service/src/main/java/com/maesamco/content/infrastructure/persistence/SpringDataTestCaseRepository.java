package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.TestCaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataTestCaseRepository extends JpaRepository<TestCase, UUID> {

    /** 삭제되지 않은 테스트케이스 단건 조회 */
    Optional<TestCase> findByIdAndDeletedAtIsNull(UUID testCaseId);

    /** 특정 문제의 공개 여부 그룹에서 가장 큰 테스트케이스 순서 조회 */
    // Spring Data JPA의 @Query로 max() 함수에 대해 직접 JPQL을 작성했다.
    // 그렇지 않으면 QueryDSL로 max() 직접 작성해야하는데,
    // QueryDSL 구현체는 최대한 사용하지 않는 방향이기에 다음과 같은 방식으로 구현한다.
    @Query("""
            select coalesce(max(tc.testCaseOrder), 0)
            from TestCase tc
            where tc.problemId = :problemId
              and tc.isPublic = :isPublic
            """)
    int findMaxTestCaseOrderByProblemIdAndIsPublic(
            @Param("problemId") UUID problemId,
            @Param("isPublic") boolean isPublic
    );

    /** 특정 문제의 승인된 테스트케이스를 공개 여부로 필터링하여 순서, id 오름차순으로 페이지 조회 */
    Page<TestCase>
    findByProblemIdAndIsPublicAndTestCaseStatusOrderByTestCaseOrderAscIdAsc(
            UUID problemId,
            boolean isPublic,
            TestCaseStatus testCaseStatus,
            Pageable pageable
    );

    /** 특정 문제의 승인된 테스트케이스를 공개 여부 내림차순, 순서와 id 오름차순으로 페이지 조회 */
    Page<TestCase>
    findByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
            UUID problemId,
            TestCaseStatus testCaseStatus,
            Pageable pageable
    );

    /** 문제 발행용 테스트케이스를 공개 여부 내림차순, 순서와 id 오름차순으로 조회 */
    List<TestCase>
    findAllByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
            UUID problemId,
            TestCaseStatus testCaseStatus
    );
}