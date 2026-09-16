package com.maesamco.content.application.service.finder;

import com.maesamco.content.application.input_port.ProblemFinder;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.repository.problem.ProblemCommandRepository;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProblemFinderService implements ProblemFinder {

    private final ProblemQueryRepository problemQueryRepository;
    private final ProblemCommandRepository problemCommandRepository;

    @Override
    @Transactional(readOnly = true)
    public Problem getById(UUID problemId) {
        return problemQueryRepository.findById(problemId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.PROBLEM_NOT_FOUND
                        )
                );
    }

    @Override
    @Transactional
    public void lockById(UUID problemId) {
        problemCommandRepository.findByIdForUpdate(problemId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.PROBLEM_NOT_FOUND
                        )
                );
    }
}