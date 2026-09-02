package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * p_ai_call_histories는 다른 Coaching 자식 테이블과 달리 UNIQUE 제약이 없다(세션당 여러 번
 * AI를 호출할 수 있음) — 그래서 다른 Repository Impl들과 달리 saveAndFlush + try-catch로
 * DataIntegrityViolationException을 잡아 409로 변환하는 로직이 없다. UNIQUE 위반 자체가
 * 발생할 수 없기 때문이다.
 */
@Repository
@RequiredArgsConstructor
public class AiCallHistoryRepositoryImpl implements AiCallHistoryRepository {

    private final SpringDataAiCallHistoryRepository springDataAiCallHistoryRepository;

    @Override
    public AiCallHistory save(AiCallHistory aiCallHistory) {
        return springDataAiCallHistoryRepository.save(aiCallHistory);
    }

    @Override
    public List<AiCallHistory> findByCoachingSessionIdOrderByCalledAtAsc(UUID coachingSessionId) {
        return springDataAiCallHistoryRepository.findByCoachingSessionIdOrderByCalledAtAsc(coachingSessionId);
    }
}
