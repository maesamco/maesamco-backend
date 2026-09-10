package com.maesamco.content.curriculum.infrastructure.persistence;

import com.maesamco.content.curriculum.domain.entity.Curriculum;
import com.maesamco.content.curriculum.domain.entity.QCurriculum;
import com.maesamco.content.curriculum.domain.repository.CurriculumSearchRepository;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CurriculumSearchRepositoryImpl implements CurriculumSearchRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Curriculum> searchCurriculums(Pageable pageable) {
        QCurriculum curriculum = QCurriculum.curriculum;

        List<Curriculum> curriculums = queryFactory
                .selectFrom(curriculum)
                .where(curriculum.deletedAt.isNull())
                .orderBy(curriculum.displayOrder.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(curriculum.count())
                .from(curriculum)
                .where(curriculum.deletedAt.isNull());

        return PageableExecutionUtils.getPage(
                curriculums,
                pageable,
                countQuery::fetchOne
        );
    }
}