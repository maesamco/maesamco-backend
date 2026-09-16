package com.maesamco.content.application.problem.service;

import com.maesamco.content.application.problem.port.ProblemVersionFinder;
import com.maesamco.content.domain.problem.entity.ProblemVersion;
import com.maesamco.content.domain.problem.repository.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 문제 버전 조회 기능을 구현하는 서비스 */
@Service
@RequiredArgsConstructor
public class ProblemVersionFinderService implements ProblemVersionFinder {

    private final ProblemVersionRepository problemVersionRepository;

    @Override
    @Transactional(readOnly = true)
    public ProblemVersion getProblemVersion(UUID problemVersionId) {
        return problemVersionRepository.findById(problemVersionId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.PROBLEM_NOT_FOUND
                        )
                );
    }
}
