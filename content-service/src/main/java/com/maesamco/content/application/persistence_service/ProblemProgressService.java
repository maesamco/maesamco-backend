package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.ProblemProgressFinder;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 문제 풀이 이력 조회 서비스 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemProgressService {

    private final ProblemProgressFinder problemProgressFinder;

    private final ProblemProgressRepository problemProgressRepository;

    /** 특정 문제에 대한 풀이 이력 단건 조회 */
    public ProblemProgress getProblemProgress(UUID userId, UUID problemId) {
        return problemProgressFinder.getByUserIdAndProblemIdOrigin(userId, problemId);
    }

    /** 현재 사용자의 문제 풀이 이력 목록을 조회 */
    public Page<ProblemProgress> getProblemProgresses(UUID userId, ProblemProgressStatus progressStatus, Pageable pageable) {
        if (progressStatus == null) {
            // 전체 조회
            return problemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        }

        // CORRECT 상태 문제들만 조회 or  WRONG 상태 문제들만 조회
        return problemProgressRepository
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                        userId,
                        progressStatus,
                        pageable
                );
    }
}
