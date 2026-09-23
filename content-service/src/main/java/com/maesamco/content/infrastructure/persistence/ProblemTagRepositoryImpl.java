package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
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
public class ProblemTagRepositoryImpl implements ProblemTagRepository {

    private final SpringDataProblemTagRepository springDataProblemTagRepository;

    @Override
    public ProblemTag save(ProblemTag problemTag) {
        return springDataProblemTagRepository.save(problemTag);
    }

    @Override
    public void delete(ProblemTag problemTag) {
        springDataProblemTagRepository.delete(problemTag);
    }

    @Override
    public boolean existsByProblemIdAndTagId(UUID problemId, UUID tagId) {
        return springDataProblemTagRepository
                .existsByProblemIdAndTagId(problemId, tagId);
    }

    @Override
    public Optional<ProblemTag> findByProblemIdAndTagId(UUID problemId, UUID tagId) {
        return springDataProblemTagRepository
                .findByProblemIdAndTagId(problemId, tagId);
    }

    @Override
    public List<ProblemTag> findAllByProblemId(UUID problemId) {
        return springDataProblemTagRepository
                .findAllByProblemId(problemId);
    }

    @Override
    public void deleteAllByTagId(UUID tagId) {
        springDataProblemTagRepository.deleteAllByTagId(tagId);
    }

    @Override
    public Page<Tag> searchTagsByProblemId(UUID problemId, Pageable pageable) {
        return springDataProblemTagRepository
                .findTagsByProblemId(problemId, pageable);
    }

    @Override
    public List<Tag> findAllTagsByProblemId(UUID problemId) {
        return springDataProblemTagRepository
                .findAllTagsByProblemId(problemId);
    }

    @Override
    public List<Tag> findDistinctTagsByProblemIdsAndAttribute(Collection<UUID> problemIds, TagAttribute attribute) {
        if (problemIds.isEmpty()) {
            return List.of();
        }

        return springDataProblemTagRepository
                .findDistinctTagsByProblemIdsAndAttribute(problemIds, attribute);
    }
}