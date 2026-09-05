package com.maesamco.content.problem.application.port;

import com.maesamco.content.problem.domain.entity.Problem;

import java.util.UUID;

/** 문제 조회 기능을 정의하는 포트 */
public interface ProblemFinder {

    Problem getProblem(UUID problemId);
}