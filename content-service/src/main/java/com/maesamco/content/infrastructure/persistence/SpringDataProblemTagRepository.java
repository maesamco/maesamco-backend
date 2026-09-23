package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataProblemTagRepository extends JpaRepository<ProblemTag, UUID> {

    /** 문제와 태그 연결 존재 여부 조회 */
    boolean existsByProblemIdAndTagId(UUID problemId, UUID tagId);

    /** 문제와 태그 연결 단건 조회 */
    Optional<ProblemTag> findByProblemIdAndTagId(UUID problemId, UUID tagId);

    /** 특정 문제의 전체 문제-태그 연결 조회 */
    List<ProblemTag> findAllByProblemId(UUID problemId);

    /** 특정 태그를 참조하는 모든 문제-태그 연결 삭제 */
    void deleteAllByTagId(UUID tagId);

    /**
     * 특정 문제의 태그 목록을 createdAt, id 내림차순으로 페이지 조회한다.
     *
     * <p>이슈 #307 — {@code Pageable}에 담긴 동적 Sort는 Spring Data가 FROM 절의
     * 첫 번째 별칭에 붙인다. {@code ProblemTag}를 먼저 쓰면(comma-FROM) 정렬 대상이
     * 아닌 {@code ProblemTag}(BaseEntity 미상속, createdAt 없음) 기준으로 정렬을
     * 시도해 500으로 이어진다 — SELECT 대상인 {@code Tag}를 FROM 절 첫 번째로 둬서
     * 동적 Sort가 항상 {@code Tag}를 기준으로 해석되게 한다.</p>
     */
    @Query(
            value = """
                    select tag
                    from Tag tag, ProblemTag problemTag
                    where problemTag.tagId = tag.id
                      and problemTag.problemId = :problemId
                    order by tag.createdAt desc, tag.id desc
                    """,
            countQuery = """
                    select count(problemTag)
                    from Tag tag, ProblemTag problemTag
                    where problemTag.tagId = tag.id
                      and problemTag.problemId = :problemId
                    """
    )
    Page<Tag> findTagsByProblemId(
            @Param("problemId") UUID problemId,
            Pageable pageable
    );

    /** 특정 문제에 연결된 모든 태그를 createdAt, id 내림차순으로 조회 */
    @Query("""
            select tag
            from ProblemTag problemTag, Tag tag
            where problemTag.tagId = tag.id
              and problemTag.problemId = :problemId
            order by tag.createdAt desc, tag.id desc
            """)
    List<Tag> findAllTagsByProblemId(
            @Param("problemId") UUID problemId
    );

    /**
     * 여러 문제 ID에 연결된 태그 중, 특정 속성(attribute)에 해당하는 태그만
     * 중복 없이 조회한다(이슈 #291).
     */
    @Query("""
            select distinct tag
            from ProblemTag problemTag, Tag tag
            where problemTag.tagId = tag.id
              and problemTag.problemId in :problemIds
              and tag.attribute = :attribute
            order by tag.name asc
            """)
    List<Tag> findDistinctTagsByProblemIdsAndAttribute(
            @Param("problemIds") Collection<UUID> problemIds,
            @Param("attribute") TagAttribute attribute
    );
}