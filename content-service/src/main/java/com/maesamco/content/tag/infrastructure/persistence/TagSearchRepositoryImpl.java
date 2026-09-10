package com.maesamco.content.tag.infrastructure.persistence;

import com.maesamco.content.tag.domain.entity.QTag;
import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import com.maesamco.content.tag.domain.repository.TagSearchRepository;
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
public class TagSearchRepositoryImpl implements TagSearchRepository {

    private final JPAQueryFactory queryFactory;

    /** 전체 태그 목록 조회 */
    @Override
    public Page<Tag> searchTags(Pageable pageable) {

        QTag tag = QTag.tag;

        List<Tag> tags = queryFactory
                .selectFrom(tag)
                .orderBy(
                        tag.createdAt.desc(),
                        tag.id.desc() // tie-breaker
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(tag.count())
                .from(tag);

        return PageableExecutionUtils.getPage(
                tags,
                pageable,
                countQuery::fetchOne
        );
    }

    /** 특정 속성의 태그 목록 조회 */
    @Override
    public Page<Tag> searchTagsByAttribute(
            TagAttribute attribute,
            Pageable pageable
    ) {

        QTag tag = QTag.tag;

        List<Tag> tags = queryFactory
                .selectFrom(tag)
                .where(tag.attribute.eq(attribute))
                .orderBy(
                        tag.createdAt.desc(),
                        tag.id.desc() // tie-breaker
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(tag.count())
                .from(tag)
                .where(tag.attribute.eq(attribute));

        return PageableExecutionUtils.getPage(
                tags,
                pageable,
                countQuery::fetchOne
        );
    }
}