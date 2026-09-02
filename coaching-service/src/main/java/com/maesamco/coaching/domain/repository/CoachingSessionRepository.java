package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.CoachingSession;

import java.util.Optional;
import java.util.UUID;

public interface CoachingSessionRepository {

    CoachingSession save(CoachingSession coachingSession);

    Optional<CoachingSession> findById(UUID id);

    Optional<CoachingSession> findBySubmissionId(UUID submissionId);

    /**
     * 같은 문제를 재시도하는 동안엔 이 세션을 계속 이어서 쓴다(2026-09-02 확정) — 이미
     * COMPLETED된 이전 세션은 여기서 안 나오므로, 그 문제를 다시 도전하면 새 세션을
     * 만들 수 있다.
     */
    Optional<CoachingSession> findInProgressByUserIdAndProblemId(UUID userId, UUID problemId);
}
