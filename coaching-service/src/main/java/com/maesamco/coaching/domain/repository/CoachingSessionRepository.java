package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.CoachingSession;

import java.util.Optional;
import java.util.UUID;

public interface CoachingSessionRepository {

    CoachingSession save(CoachingSession coachingSession);

    Optional<CoachingSession> findById(UUID id);

    Optional<CoachingSession> findBySubmissionId(UUID submissionId);

    /**
     * 문제당 세션은 평생 최대 1개다(2026-09-03 재검토, 이슈 #84 — V4의 IN_PROGRESS 한정
     * 조회에서 상태 무관 조회로 변경). COMPLETED된 세션이 있어도 그대로 반환하므로,
     * 이미 코칭이 끝난 문제를 다시 정답 제출해도 새 세션을 만들지 않고 기존 세션을
     * 재사용한다.
     */
    Optional<CoachingSession> findByUserIdAndProblemId(UUID userId, UUID problemId);
}
