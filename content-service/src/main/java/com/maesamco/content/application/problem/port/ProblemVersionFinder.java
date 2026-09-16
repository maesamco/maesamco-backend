package com.maesamco.content.application.problem.port;

import com.maesamco.content.domain.problem.entity.ProblemVersion;

import java.util.UUID;

/** 문제 버전 조회 기능을 정의하는 포트 */
public interface ProblemVersionFinder {

    ProblemVersion getProblemVersion(UUID problemVersionId);
}
