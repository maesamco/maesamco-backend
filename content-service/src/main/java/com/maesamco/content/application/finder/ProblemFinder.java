package com.maesamco.content.application.finder;

import com.maesamco.content.domain.entity.problem.Problem;

import java.util.UUID;

/** 문제 조회 기능을 정의하는 포트 */
public interface ProblemFinder {

    /** 문제 단건 조회 */
    Problem getById(UUID problemId);

    /** 테스트케이스 순번 계산 등 문제 단위 동시성 제어가 필요한 경우 사용합니다. */
    Problem lockById(UUID problemId);
}
