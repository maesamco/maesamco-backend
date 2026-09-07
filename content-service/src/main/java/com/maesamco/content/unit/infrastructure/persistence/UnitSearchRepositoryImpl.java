package com.maesamco.content.unit.infrastructure.persistence;

import com.maesamco.content.unit.domain.entity.QUnit;
import com.maesamco.content.unit.domain.entity.Unit;
import com.maesamco.content.unit.domain.repository.UnitSearchRepository;
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

/**
 * Querydsl을 사용하여 특정 커리큘럼의 유닛 목록을 조회하는 Repository
 *
 * <ul>
 *     <li>검색: curriculumId</li>
 *     <li>페이징: page, size</li>
 *     <li>정렬: displayOrder 오름차순</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class UnitSearchRepositoryImpl implements UnitSearchRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 특정 커리큘럼에 속한 삭제되지 않은 유닛 목록을 조회합니다.
     *
     * @param curriculumId 커리큘럼 식별자
     * @param pageable 페이징 조건
     * @return 유닛 페이지
     */
    @Override
    public Page<Unit> searchUnits(UUID curriculumId, Pageable pageable) {
        QUnit unit = QUnit.unit;

        // where절 공통 부분 묶기
        BooleanExpression[] conditions = {
                curriculumIdEq(unit, curriculumId),
                unit.deletedAt.isNull()
        };

        List<Unit> units = queryFactory
                .selectFrom(unit)
                .where(conditions)
                .orderBy(unit.displayOrder.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(unit.count())
                .from(unit)
                .where(conditions);

        return PageableExecutionUtils.getPage(
                units,
                pageable,
                countQuery::fetchOne
        );
    }

    /** 커리큘럼 식별자 일치 조건을 생성합니다. */
    private BooleanExpression curriculumIdEq(QUnit unit, UUID curriculumId) {
        if (curriculumId == null) { return null; }

        return unit.curriculumId.eq(curriculumId);
    }
}