package com.maesamco.content.application.input_port;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemProgressFinder {

    /** 사용자와 문제 기준으로 기존 문제 풀이 진행 상태를 조회합니다. */
    Optional<ProblemProgress> getByUserIdAndProblemId(UUID userId, UUID problemId);

    /** 사용자의 전체 문제 풀이 진행 이력을 조회합니다. */
    List<ProblemProgress> getByUserId(UUID userId);

    /** 사용자의 특정 문제 풀이 상태에 해당하는 진행 이력 목록을 조회합니다. */
    List<ProblemProgress> getByUserIdAndProgressStatus(UUID userId, ProblemProgressStatus progressStatus);
}