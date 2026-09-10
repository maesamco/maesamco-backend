package com.maesamco.content.lesson.infrastructure.persistence;

import com.maesamco.content.lesson.domain.entity.Lesson;
import com.maesamco.content.lesson.domain.entity.QLesson;
import com.maesamco.content.lesson.domain.repository.LessonSearchRepository;
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
 * Querydsl을 사용하여 특정 유닛의 레슨 목록을 조회하는 Repository
 *
 * <ul>
 *     <li>검색: unitId</li>
 *     <li>페이징: page, size</li>
 *     <li>정렬: displayOrder 오름차순</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class LessonSearchRepositoryImpl implements LessonSearchRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 특정 유닛에 속한 삭제되지 않은 레슨 목록을 조회합니다.
     *
     * @param unitId 유닛 식별자
     * @param pageable 페이징 조건
     * @return 레슨 페이지
     */
    @Override
    public Page<Lesson> searchLessons(UUID unitId, Pageable pageable) {
        QLesson lesson = QLesson.lesson;

        // where절 공통 부분 묶기
        BooleanExpression[] conditions = {
                unitIdEq(lesson, unitId),
                lesson.deletedAt.isNull()
        };

        List<Lesson> lessons = queryFactory
                .selectFrom(lesson)
                .where(conditions)
                .orderBy(lesson.displayOrder.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(lesson.count())
                .from(lesson)
                .where(conditions);

        return PageableExecutionUtils.getPage(
                lessons,
                pageable,
                countQuery::fetchOne
        );
    }

    /** 유닛 식별자 일치 조건을 생성합니다. */
    private BooleanExpression unitIdEq(QLesson lesson, UUID unitId) {
        if (unitId == null) { return null; }

        return lesson.unitId.eq(unitId);
    }
}