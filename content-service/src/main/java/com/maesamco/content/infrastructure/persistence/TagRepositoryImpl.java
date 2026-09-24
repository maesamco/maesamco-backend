package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.repository.TagRepository;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.infrastructure.persistence.support.SpringPageConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TagRepositoryImpl implements TagRepository {

    private final SpringDataTagRepository springDataTagRepository;

    @Override
    public Tag save(Tag tag) {
        return springDataTagRepository.save(tag);
    }

    @Override
    public boolean existsByName(String name) {
        return springDataTagRepository.existsByName(name);
    }

    @Override
    public Optional<Tag> findById(UUID tagId) {
        return springDataTagRepository.findByIdAndDeletedAtIsNull(tagId);
    }

    @Override
    public List<Tag> findAllByIds(Collection<UUID> tagIds) {
        return springDataTagRepository.findAllByIdInAndDeletedAtIsNull(tagIds);
    }

    @Override
    public PageResult<Tag> searchTags(PageQuery pageQuery) {
        return SpringPageConverter.toPageResult(
                springDataTagRepository
                        .findAllByOrderByCreatedAtDescIdDesc(SpringPageConverter.toPageable(pageQuery))
        );
    }

    @Override
    public PageResult<Tag> searchTagsByAttribute(
            TagAttribute attribute,
            PageQuery pageQuery
    ) {
        return SpringPageConverter.toPageResult(
                springDataTagRepository
                        .findByAttributeOrderByCreatedAtDescIdDesc(
                                attribute,
                                SpringPageConverter.toPageable(pageQuery)
                        )
        );
    }
}
