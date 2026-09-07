package com.maesamco.content.tag.domain.repository;

import com.maesamco.content.tag.domain.entity.Tag;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID>, TagSearchRepository {

    /** 동일한 태그 이름 존재 여부 조회 */
    boolean existsByName(String name);

    /** 삭제되지 않은 태그 단건 조회 */
    @NonNull Optional<Tag> findById(@NonNull UUID id);
}