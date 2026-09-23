package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataTagRepository extends JpaRepository<Tag, UUID> {

    /** 동일한 태그 이름 존재 여부 조회 */
    boolean existsByName(String name);

    /** 삭제되지 않은 태그 단건 조회 */
    Optional<Tag> findByIdAndDeletedAtIsNull(UUID tagId);

    /** 삭제되지 않은 태그 일괄 조회 */
    List<Tag> findAllByIdInAndDeletedAtIsNull(Collection<UUID> tagIds);

    /** 태그 목록을 createdAt, id 내림차순으로 페이지 조회 */
    Page<Tag> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /** 특정 속성의 태그 목록을 createdAt, id 내림차순으로 페이지 조회 */
    Page<Tag> findByAttributeOrderByCreatedAtDescIdDesc(
            TagAttribute attribute,
            Pageable pageable
    );
}
