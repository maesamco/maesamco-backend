package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.Hint;
import com.maesamco.coaching.domain.repository.HintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HintRepositoryImpl implements HintRepository {

    private final SpringDataHintRepository springDataHintRepository;

    @Override
    public Hint save(Hint hint) {
        return springDataHintRepository.save(hint);
    }

    @Override
    public List<Hint> findByCoachingSessionId(UUID coachingSessionId) {
        return springDataHintRepository.findByCoachingSessionIdOrderByStageAsc(coachingSessionId);
    }

    @Override
    public Optional<Hint> findByCoachingSessionIdAndStage(UUID coachingSessionId, int stage) {
        return springDataHintRepository.findByCoachingSessionIdAndStage(coachingSessionId, stage);
    }
}
