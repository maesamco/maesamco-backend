package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.infrastructure.persistence.support.SpringPageConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    public PageResult<Tag> searchTagsByProblemId(UUID problemId, PageQuery pageQuery) {
        // 이 쿼리는 정렬(tag.createdAt desc, tag.id desc)을 JPQL에 고정하고 있다. 요청의 동적 Sort를 넘기면
        // 같은 컬럼이 order by에 중복으로 붙으므로(#307), 페이지 번호와 크기만 전달한다.
        return SpringPageConverter.toPageResult(
                springDataProblemTagRepository.findTagsByProblemId(
                        problemId,
                        PageRequest.of(pageQuery.page(), pageQuery.size())
                )
        );
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