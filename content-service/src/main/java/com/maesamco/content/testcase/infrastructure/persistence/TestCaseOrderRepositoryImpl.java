package com.maesamco.content.testcase.infrastructure.persistence;

import com.maesamco.content.testcase.domain.entity.QTestCase;
import com.maesamco.content.testcase.domain.repository.TestCaseOrderRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** 테스트케이스 order max 조회 Repository 구현체 */
@Repository
@RequiredArgsConstructor
public class TestCaseOrderRepositoryImpl implements TestCaseOrderRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public int findMaxTestCaseOrderByProblemIdAndIsPublic(UUID problemId, boolean isPublic) {
        QTestCase testCase = QTestCase.testCase;

        Integer maxOrder = queryFactory
                .select(testCase.testCaseOrder.max())
                .from(testCase)
                .where(
                        testCase.problemId.eq(problemId),
                        testCase.isPublic.eq(isPublic)
                        // PENDING 된 order값은 넘기고 그보다 큰 값으로 저장시키기 위해서 APPROVED가 아니여도 포함시킨다.
                        // testCase.testCaseStatus.eq(TestCaseStatus.APPROVED)
                )
                .fetchOne();

        return maxOrder == null ? 0 : maxOrder; // 없으면 0을 반환
    }
}