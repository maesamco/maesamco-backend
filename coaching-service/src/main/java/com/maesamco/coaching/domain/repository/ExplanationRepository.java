package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.Explanation;

import java.util.Optional;
import java.util.UUID;

public interface ExplanationRepository {

    Explanation save(Explanation explanation);

    Optional<Explanation> findBySubmissionId(UUID submissionId);
}
