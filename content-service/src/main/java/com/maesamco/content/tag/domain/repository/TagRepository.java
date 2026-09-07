package com.maesamco.content.tag.domain.repository;

import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    /** 태그 이름 존재 여부 조회 */
    boolean existsByName(String name);

    /** 삭제되지 않은 태그 단건 조회 */
    @NonNull Optional<Tag> findById(@NonNull UUID id);

    /** 삭제되지 않은 전체 태그 목록 조회 */
    List<Tag> findAllBy();

    /** 삭제되지 않은 특정 속성의 태그 목록 조회 */
    List<Tag> findAllByAttribute(TagAttribute attribute);
}