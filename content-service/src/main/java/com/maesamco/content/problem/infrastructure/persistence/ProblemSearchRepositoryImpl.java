package com.maesamco.content.problem.infrastructure.persistence;

import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.QProblem;
import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.repository.ProblemSearchRepository;
import com.maesamco.content.problem.presentation.dto.request.ProblemSearchRequest;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Querydsl을 사용하여 문제 검색, 페이징 및 다중 정렬 조회를 수행하는 Repository
 *
 * <ul>
 *     <li>검색: language, difficulty, type, source</li>
 *     <li>페이징: page, size</li>
 *     <li>정렬: Pageable의 sort 조건</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class ProblemSearchRepositoryImpl implements ProblemSearchRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 전달된 검색 조건을 기준으로 삭제되지 않은 문제 목록을 조회합니다.
     *
     * <p>개별 검색 조건은 AND로 연결되며,
     * 값이 null인 검색 조건은 Querydsl where절에서 제외됩니다.</p>
     *
     * @param request 문제 검색 조건
     * @param pageable 페이징 및 정렬 조건
     * @return 검색된 문제 페이지
     */
    @Override
    public Page<Problem> searchProblems(ProblemSearchRequest request, Pageable pageable) {
        QProblem problem = QProblem.problem;

        // where절 공통 부분 묶기
        BooleanExpression[] conditions = {
                languageEq(problem, request),
                difficultyEq(problem, request),
                typeEq(problem, request),
                sourceEq(problem, request),
                statusEq(problem, request)
        };

        List<Problem> problems = queryFactory
                .selectFrom(problem)
                .where(conditions)
                .orderBy(toOrderSpecifiers(problem, pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(problem.count())
                .from(problem)
                .where(conditions);

        return PageableExecutionUtils.getPage(
                problems,
                pageable,
                countQuery::fetchOne
        );
    }



    /** 프로그래밍 언어 일치 조건을 생성합니다. */
    private BooleanExpression languageEq(QProblem problem, ProblemSearchRequest request) {
        if (request == null || request.getLanguage() == null) { return null; }

        return problem.language.eq(request.getLanguage());
    }
    /** 문제 난이도 일치 조건을 생성합니다. */
    private BooleanExpression difficultyEq(QProblem problem, ProblemSearchRequest request) {
        if (request == null || request.getDifficulty() == null) { return null; }

        return problem.difficulty.eq(request.getDifficulty());
    }
    /** 문제 유형 일치 조건을 생성합니다. */
    private BooleanExpression typeEq(QProblem problem, ProblemSearchRequest request) {
        if (request == null || request.getType() == null) { return null; }

        return problem.type.eq(request.getType());
    }
    /** 문제 출처 일치 조건을 생성합니다. */
    private BooleanExpression sourceEq(QProblem problem, ProblemSearchRequest request) {
        if (request == null || request.getSource() == null) { return null; }

        return problem.source.eq(request.getSource());
    }
    /** 문제 상태 일치 조건을 생성합니다. */
    private BooleanExpression statusEq(QProblem problem, ProblemSearchRequest request) {
        if (request == null || request.getProblemStatus() == null) { return null; }

        return problem.problemStatus.eq(request.getProblemStatus());
    }

    /**
     * Pageable의 모든 정렬 조건을 Querydsl 정렬 조건으로 변환합니다.
     *
     * <p>지원하는 정렬 조건이 없으면 생성일 내림차순을 기본값으로 적용합니다.</p>
     */
    private OrderSpecifier<?>[] toOrderSpecifiers(QProblem problem, Pageable pageable) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();

        if (pageable != null && pageable.getSort().isSorted()) {
            for (Sort.Order sortOrder : pageable.getSort()) { // pageable.getSort()는 pageable 안에 있는 모든 Sort 객체를 하나씩 꺼내주는 함수다
                Order direction = sortOrder.isAscending() ? Order.ASC : Order.DESC;

                OrderSpecifier<?> orderSpecifier = createOrderSpecifier(problem, sortOrder.getProperty(), direction);

                if (orderSpecifier != null) {
                    orderSpecifiers.add(orderSpecifier);
                }
            }
        }

        if (orderSpecifiers.isEmpty()) {
            orderSpecifiers.add(problem.createdAt.desc()); // 정렬 조건이 없다면 생성일 기준으로 내림차순 정렬 (최신순)
        }

        // tie-breaker 도입: 동일한 정렬 값을 가진 행의 순서를 고정해 페이징 결과를 안정적으로 유지
        orderSpecifiers.add(problem.id.desc());

        return orderSpecifiers.toArray(OrderSpecifier<?>[]::new);
    }

    /** 허용된 문제 필드에 대해서만 Querydsl 정렬 조건을 생성합니다. */
    private OrderSpecifier<?> createOrderSpecifier(QProblem problem, String property, Order direction) {
        return switch (property) {
            case "title"        -> new OrderSpecifier<>(direction, problem.title);
            case "language"     -> new OrderSpecifier<>(direction, problem.language);
            case "difficulty"   -> createDifficultyOrderSpecifier(problem, direction); // enum 이름 값이 아닌, (EASY → MEDIUM → HARD) 기준으로 정렬
            case "type"         -> new OrderSpecifier<>(direction, problem.type);
            case "source"       -> new OrderSpecifier<>(direction, problem.source);
            case "createdAt"    -> new OrderSpecifier<>(direction, problem.createdAt);
            case "updatedAt"    -> new OrderSpecifier<>(direction, problem.updatedAt);
            default -> null;
        };
    }

    /** enum */
    private OrderSpecifier<Integer> createDifficultyOrderSpecifier(QProblem problem, Order direction) {
        NumberExpression<Integer> difficultyOrder = new CaseBuilder()
                .when(problem.difficulty.eq(ProblemDifficulty.EASY)).then(1)
                .when(problem.difficulty.eq(ProblemDifficulty.MEDIUM)).then(2)
                .when(problem.difficulty.eq(ProblemDifficulty.HARD)).then(3)
                .otherwise(999);

        return new OrderSpecifier<>(direction, difficultyOrder);
    }
}