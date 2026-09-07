package com.maesamco.content.testcase.infrastructure.persistence;

import com.maesamco.content.testcase.domain.entity.QTestCase;
import com.maesamco.content.testcase.domain.entity.TestCase;
import com.maesamco.content.testcase.domain.enums.TestCaseStatus;
import com.maesamco.content.testcase.domain.repository.TestCaseSearchRepository;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** 테스트케이스 검색 Repository 구현체 */
@Repository
@RequiredArgsConstructor
public class TestCaseSearchRepositoryImpl implements TestCaseSearchRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<TestCase> searchTestCases(UUID problemId, boolean isPublic, Pageable pageable) {
        QTestCase testCase = QTestCase.testCase;

        BooleanExpression[] conditions = {
                testCase.problemId.eq(problemId),
                testCase.isPublic.eq(isPublic), // 공개 or 비공개 중 택 1
                testCase.testCaseStatus.eq(TestCaseStatus.APPROVED)
        };

        List<TestCase> testCases = queryFactory
                .selectFrom(testCase)
                .where(conditions)
                .orderBy(
                        testCase.testCaseOrder.asc(),
                        testCase.id.asc() // tie-breaker 도입
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(testCase.count())
                .from(testCase)
                .where(conditions);

        return PageableExecutionUtils.getPage(
                testCases,
                pageable,
                countQuery::fetchOne
        );
    }

    @Override
    public Page<TestCase> searchTestCasesAll(UUID problemId, Pageable pageable) {
        QTestCase testCase = QTestCase.testCase;

        BooleanExpression[] conditions = {
                testCase.problemId.eq(problemId),
                testCase.testCaseStatus.eq(TestCaseStatus.APPROVED)
        };

        List<TestCase> testCases = queryFactory
                .selectFrom(testCase)
                .where(conditions)
                .orderBy(
                        testCase.isPublic.desc(), // 공개(true) 먼저 오도록
                        testCase.testCaseOrder.asc(),
                        testCase.id.asc() // tie-breaker
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(testCase.count())
                .from(testCase)
                .where(conditions);

        return PageableExecutionUtils.getPage(
                testCases,
                pageable,
                countQuery::fetchOne
        );
    }
}