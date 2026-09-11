package com.maesamco.content.problem.application.port;

import com.maesamco.content.problem.domain.entity.Problem;

import java.util.UUID;

/** 문제 조회 기능을 정의하는 포트 */
public interface ProblemFinder {

    Problem getProblem(UUID problemId);

    /**
     * 테스트케이스 순번 계산 등 문제 단위 동시성 제어가 필요한 경우 사용합니다.
     */
    Problem getProblemForUpdate(UUID problemId);
}
