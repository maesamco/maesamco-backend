package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<Tag> searchTags(Pageable pageable) {
        return springDataTagRepository
                .findAllByOrderByCreatedAtDescIdDesc(pageable);
    }

    @Override
    public Page<Tag> searchTagsByAttribute(
            TagAttribute attribute,
            Pageable pageable
    ) {
        return springDataTagRepository
                .findByAttributeOrderByCreatedAtDescIdDesc(
                        attribute,
                        pageable
                );
    }
}
