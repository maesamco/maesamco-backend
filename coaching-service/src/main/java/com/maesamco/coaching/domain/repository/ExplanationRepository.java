package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.Explanation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExplanationRepository {

    Explanation save(Explanation explanation);

    Optional<Explanation> findBySubmissionId(UUID submissionId);

    Optional<Explanation> findById(UUID id);

    /**
     * 이슈 #52 — 재시도 대상 재구성용. 같은 세션에서 재도전(재제출)마다 새 Explanation이
     * 생길 수 있어(이슈 #84) 단건이 아니라 목록으로 반환한다 — 호출자가 세션 완료
     * 시점(completedAt)과 매칭되는 것을 골라야 한다.
     */
    List<Explanation> findByCoachingSessionId(UUID coachingSessionId);
}
