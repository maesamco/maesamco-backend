package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository {

    Tag save(Tag tag);

    boolean existsByName(String name);

    Optional<Tag> findById(UUID tagId);

    List<Tag> findAllByIds(Collection<UUID> tagIds);

    Page<Tag> searchTags(Pageable pageable);

    Page<Tag> searchTagsByAttribute(TagAttribute attribute, Pageable pageable);
}
