package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProblemVersionRepositoryImpl implements ProblemVersionRepository {

    private final SpringDataProblemVersionRepository springDataProblemVersionRepository;

    @Override
    public ProblemVersion save(ProblemVersion problemVersion) {
        return springDataProblemVersionRepository.save(problemVersion);
    }

    @Override
    public Optional<ProblemVersion> findById(UUID problemVersionId) {
        return springDataProblemVersionRepository.findById(problemVersionId);
    }

    @Override
    public Optional<ProblemVersion> findByProblemIdAndId(UUID problemId, UUID id) {
        return springDataProblemVersionRepository.findByProblemIdAndId(problemId, id);
    }


    @Override
    public void flush() {
        springDataProblemVersionRepository.flush();
    }

    @Override
    public Optional<ProblemVersion> findByProblemIdAndVersionNo(UUID problemId, Integer versionNo) {
        return springDataProblemVersionRepository
                .findByProblemIdAndVersionNo(problemId, versionNo);
    }

    @Override
    public List<ProblemVersion> findAllByProblemIdOrderByVersionNoDesc(UUID problemId) {
        return springDataProblemVersionRepository
                .findAllByProblemIdOrderByVersionNoDesc(problemId);
    }
}