package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.common.pagination.PageQuery;
import com.maesamco.content.domain.common.pagination.PageResult;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.QProblem;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.domain.repository.problem.ProblemSearchCondition;
import com.maesamco.content.infrastructure.persistence.support.SpringPageConverter;
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
import java.util.Optional;
import java.util.UUID;

/**
 * derived query로 표현하기에 복잡한 구조이기에 QueryDSL로 구현한다.
 *
 * <p>Spring Data의 {@link Pageable} / {@link Page}는 이 구현체 내부에서만 사용하고,
 * Domain 계약에는 {@link PageQuery} / {@link PageResult}로 노출한다(#230).</p>
 */

@Repository
@RequiredArgsConstructor
public class ProblemQueryRepositoryImpl implements ProblemQueryRepository {

    private final SpringDataProblemRepository springDataProblemRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Problem> findById(UUID problemId) {
        return springDataProblemRepository.findByIdAndDeletedAtIsNull(problemId);
    }

    @Override
    public List<UUID> findProblemIdsByLessonId(UUID lessonId) {
        return springDataProblemRepository.findAllByLessonIdAndDeletedAtIsNull(lessonId)
                .stream()
                .map(Problem::getId)
                .toList();
    }

    @Override
    public PageResult<Problem> searchProblems(ProblemSearchCondition condition, PageQuery pageQuery) {
        Pageable pageable = SpringPageConverter.toPageable(pageQuery);
        QProblem problem = QProblem.problem;

        BooleanExpression[] conditions = {
                languageEq(problem, condition),
                difficultyEq(problem, condition),
                typeEq(problem, condition),
                sourceEq(problem, condition),
                statusEq(problem, condition),
                lessonIdEq(problem, condition)
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

        // 마지막 페이지 등 content만으로 전체 개수를 알 수 있으면 count 쿼리를 생략한다.
        Page<Problem> page = PageableExecutionUtils.getPage(
                problems,
                pageable,
                countQuery::fetchOne
        );

        return SpringPageConverter.toPageResult(page);
    }

    private BooleanExpression languageEq(QProblem problem, ProblemSearchCondition condition) {
        if (condition == null || condition.getLanguage() == null) {
            return null;
        }

        return problem.language.eq(condition.getLanguage());
    }

    private BooleanExpression difficultyEq(QProblem problem, ProblemSearchCondition condition) {
        if (condition == null || condition.getDifficulty() == null) {
            return null;
        }

        return problem.difficulty.eq(condition.getDifficulty());
    }

    private BooleanExpression typeEq(QProblem problem, ProblemSearchCondition condition) {
        if (condition == null || condition.getType() == null) {
            return null;
        }

        return problem.type.eq(condition.getType());
    }

    private BooleanExpression sourceEq(QProblem problem, ProblemSearchCondition condition) {
        if (condition == null || condition.getSource() == null) {
            return null;
        }

        return problem.source.eq(condition.getSource());
    }

    private BooleanExpression statusEq(QProblem problem, ProblemSearchCondition condition) {
        if (condition == null || condition.getProblemStatus() == null) {
            return null;
        }

        return problem.problemStatus.eq(condition.getProblemStatus());
    }

    private BooleanExpression lessonIdEq(QProblem problem, ProblemSearchCondition condition) {
        if (condition == null || condition.getLessonId() == null) {
            return null;
        }

        return problem.lessonId.eq(condition.getLessonId());
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(QProblem problem, Pageable pageable) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();

        if (pageable != null && pageable.getSort().isSorted()) {
            for (Sort.Order sortOrder : pageable.getSort()) {
                Order direction =
                        sortOrder.isAscending()
                                ? Order.ASC
                                : Order.DESC;

                OrderSpecifier<?> orderSpecifier =
                        createOrderSpecifier(problem, sortOrder.getProperty(), direction);

                if (orderSpecifier != null) {
                    orderSpecifiers.add(orderSpecifier);
                }
            }
        }

        if (orderSpecifiers.isEmpty()) {
            orderSpecifiers.add(problem.createdAt.desc());
        }

        orderSpecifiers.add(problem.id.desc());

        return orderSpecifiers.toArray(OrderSpecifier<?>[]::new);
    }

    private OrderSpecifier<?> createOrderSpecifier(QProblem problem, String property, Order direction) {
        return switch (property) {
            case "title" ->
                    new OrderSpecifier<>(direction, problem.title);
            case "language" ->
                    new OrderSpecifier<>(direction, problem.language);
            case "difficulty" ->
                    createDifficultyOrderSpecifier(problem, direction);
            case "type" ->
                    new OrderSpecifier<>(direction, problem.type);
            case "source" ->
                    new OrderSpecifier<>(direction, problem.source);
            case "createdAt" ->
                    new OrderSpecifier<>(direction, problem.createdAt);
            case "updatedAt" ->
                    new OrderSpecifier<>(direction, problem.updatedAt);
            default -> null;
        };
    }

    private OrderSpecifier<Integer> createDifficultyOrderSpecifier(QProblem problem, Order direction) {
        NumberExpression<Integer> difficultyOrder =
                new CaseBuilder()
                        .when(problem.difficulty.eq(ProblemDifficulty.EASY))
                        .then(1)
                        .when(problem.difficulty.eq(ProblemDifficulty.MEDIUM))
                        .then(2)
                        .when(problem.difficulty.eq(ProblemDifficulty.HARD))
                        .then(3)
                        .otherwise(999);

        return new OrderSpecifier<>(direction, difficultyOrder);
    }
}