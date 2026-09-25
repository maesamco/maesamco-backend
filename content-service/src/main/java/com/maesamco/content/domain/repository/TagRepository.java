package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.domain.entity.TagAttribute;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository {

    Tag save(Tag tag);

    boolean existsByName(String name);

    Optional<Tag> findById(UUID tagId);

    List<Tag> findAllByIds(Collection<UUID> tagIds);

    PageResult<Tag> searchTags(PageQuery pageQuery);

    PageResult<Tag> searchTagsByAttribute(TagAttribute attribute, PageQuery pageQuery);
}
