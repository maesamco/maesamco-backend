package com.maesamco.content.problem.infrastructure.persistence;

import com.maesamco.content.tag.domain.entity.QProblemTag;
import com.maesamco.content.tag.domain.entity.QTag;
import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.problem.domain.repository.ProblemTagSearchRepository;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProblemTagSearchRepositoryImpl implements ProblemTagSearchRepository {

    private final JPAQueryFactory queryFactory;

    /** 특정 문제의 태그 목록 조회 */
    @Override
    public Page<Tag> searchTagsByProblemId(UUID problemId, Pageable pageable) {

        QProblemTag problemTag = QProblemTag.problemTag;
        QTag tag = QTag.tag;

        // 특정 problemId에 연결되어 있는 태그들을 p_problem_tags와 p_tags를 Join해서 조회한다.
        List<Tag> tags = queryFactory
                .select(tag)
                .from(problemTag) // p_problem_tags 테이블을 가지고
                .join(tag) // p_tags 테이블과 JOIN
                .on(problemTag.tagId.eq(tag.id)) // problem_tag.tag_id = tag.id 조건으로 JOIN
                .where(problemTag.problemId.eq(problemId)) // 특정 problemId에 연결된 태그만 조회
                .orderBy(
                        tag.createdAt.desc(),
                        tag.id.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(problemTag.count())
                .from(problemTag)
                .join(tag)
                .on(problemTag.tagId.eq(tag.id))
                .where(problemTag.problemId.eq(problemId));

        return PageableExecutionUtils.getPage(
                tags,
                pageable,
                countQuery::fetchOne
        );
    }
}