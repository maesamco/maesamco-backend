package com.maesamco.coaching.application;

import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 문제당 세션은 상태와 무관하게 평생 최대 1개다(2026-09-03 재검토, 이슈 #84 — V4의
 * "IN_PROGRESS인 세션만 이어서 쓰고 COMPLETED 후 재도전 시 새 세션"에서 변경). 오답 힌트
 * 요청(HintGenerationFacade)과 60초 설명 등록(ExplanationGenerationFacade) 양쪽에서
 * 완전히 동일한 find-or-create 로직이 필요해서 공용으로 뺐다 — 두 Facade가 각자 이 로직을
 * 들고 있으면 나중에 한쪽만 고치고 다른 쪽을 놓치는 드리프트가 생기기 쉽다.
 */
@Component
public class CoachingSessionFinder {

    private final CoachingSessionRepository coachingSessionRepository;

    public CoachingSessionFinder(CoachingSessionRepository coachingSessionRepository) {
        this.coachingSessionRepository = coachingSessionRepository;
    }

    /**
     * 이미 세션이 있으면 재사용(재시도마다 최신 제출로 submission_id를 갈아탐), 없으면
     * 새로 만든다. 동시 요청으로 두 트랜잭션이 동시에 "없다"고 판단해 둘 다 생성을
     * 시도하면, 나중에 flush되는 쪽이 UNIQUE(user_id, problem_id) 위반으로
     * COACHING_SESSION_ALREADY_EXISTS를 받는다 — 이 경우 방금 다른 트랜잭션이 만든 세션을
     * 그대로 재조회해서 쓴다(힌트 요청·설명 등록 자체를 실패시킬 이유가 없다, PR #70 리뷰와
     * 동일한 판단).
     */
    public CoachingSession findOrCreate(SubmissionSnapshot submission) {
        return coachingSessionRepository.findByUserIdAndProblemId(submission.userId(), submission.problemId())
                .map(session -> {
                    if (!session.getSubmissionId().equals(submission.submissionId())) {
                        session.updateSubmissionId(submission.submissionId());
                        return coachingSessionRepository.save(session);
                    }
                    return session;
                })
                .orElseGet(() -> {
                    try {
                        return coachingSessionRepository.save(
                                CoachingSession.create(submission.submissionId(), submission.userId(), submission.problemId())
                        );
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.COACHING_SESSION_ALREADY_EXISTS) {
                            return coachingSessionRepository.findByUserIdAndProblemId(submission.userId(), submission.problemId())
                                    .orElseThrow(() -> e);
                        }
                        throw e;
                    }
                });
    }
}
