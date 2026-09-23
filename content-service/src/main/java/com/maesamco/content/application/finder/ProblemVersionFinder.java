package com.maesamco.content.application.finder;

import com.maesamco.content.domain.entity.problem.ProblemVersion;

import java.util.UUID;

/** 문제 버전 조회 기능을 정의하는 포트 */
public interface ProblemVersionFinder {

    /** 문제 버전 단건 조회 */
    ProblemVersion getById(UUID problemVersionId);

    /** 문제에 속한 문제 버전 단건 조회 */
    ProblemVersion getByProblemIdAndId(UUID problemId, UUID problemVersionId);
}
