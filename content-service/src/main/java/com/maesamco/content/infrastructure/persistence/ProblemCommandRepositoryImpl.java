package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.repository.problem.ProblemCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProblemCommandRepositoryImpl
        implements ProblemCommandRepository {

    private final SpringDataProblemRepository springDataProblemRepository;

    @Override
    public Problem save(Problem problem) {
        return springDataProblemRepository.save(problem);
    }

    @Override
    public Optional<Problem> findByIdForUpdate(UUID problemId) {
        return springDataProblemRepository.findByIdForUpdate(problemId);
    }

    @Override
    public void flush() {
        springDataProblemRepository.flush();
    }
}