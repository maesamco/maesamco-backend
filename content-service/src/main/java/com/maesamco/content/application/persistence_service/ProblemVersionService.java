package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 관리자용 문제 버전 이력 조회 서비스입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemVersionService {

    private final ProblemFinder problemFinder;
    private final ProblemVersionRepository problemVersionRepository;

    /**
     * 특정 문제의 전체 버전 이력을 최신 버전부터 조회합니다.
     */
    public List<ProblemVersion> getProblemVersions(
            UUID problemId
    ) {
        problemFinder.getById(
                problemId
        );

        return problemVersionRepository
                .findAllByProblemIdOrderByVersionNoDesc(
                        problemId
                );
    }

    /**
     * 특정 문제의 버전 번호에 해당하는 스냅샷을 조회합니다.
     */
    public ProblemVersion getProblemVersion(
            UUID problemId,
            Integer versionNo
    ) {
        problemFinder.getById(
                problemId
        );

        return problemVersionRepository
                .findByProblemIdAndVersionNo(
                        problemId,
                        versionNo
                )
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.PROBLEM_VERSION_NOT_FOUND
                        )
                );
    }
}
