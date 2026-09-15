package com.maesamco.content.application.input_port;

import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.repository.problem.ProblemRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 문제 조회 기능을 구현하는 서비스 */
@Service
@RequiredArgsConstructor
public class ProblemFinderService implements ProblemFinder {

    private final ProblemRepository problemRepository;

    @Override
    @Transactional(readOnly = true)
    public Problem getProblem(UUID problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.PROBLEM_NOT_FOUND
                        )
                );
    }

    @Override
    @Transactional
    public Problem getProblemForUpdate(UUID problemId) {
        return problemRepository.findByIdForUpdate(problemId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.PROBLEM_NOT_FOUND
                        )
                );
    }
}
