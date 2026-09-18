package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 특정 문제의 태그 목록을 createdAt, id 내림차순으로 페이지 조회 */
    @Query(
            value = """
                    select tag
                    from ProblemTag problemTag, Tag tag
                    where problemTag.tagId = tag.id
                      and problemTag.problemId = :problemId
                    order by tag.createdAt desc, tag.id desc
                    """,
            countQuery = """
                    select count(problemTag)
                    from ProblemTag problemTag, Tag tag
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
}