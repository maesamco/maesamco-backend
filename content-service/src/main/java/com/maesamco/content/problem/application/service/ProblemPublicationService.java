package com.maesamco.content.problem.application.service;

import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.domain.entity.Problem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 관리자의 문제 발행 승인을 처리하는 애플리케이션 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class ProblemPublicationService {

    private final ProblemFinder problemFinder;

    /**
     * REVIEW_PENDING 상태의 문제를 PUBLISHED 상태로 전환합니다.
     *
     * @param problemId 발행을 승인할 문제 식별자
     */
    @Transactional(rollbackFor = Exception.class)
    public void approvePublication(UUID problemId) {
        Problem problem = problemFinder.getProblem(problemId);

        problem.approvePublication();
    }
}
