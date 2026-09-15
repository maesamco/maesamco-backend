package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.AiCallPurpose;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
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

    // PR #182 리뷰(용현님 P2) — SKIPPED(호출 자체 없었음)와 INFRA_FAILED(호출은 했지만
    // 인프라 실패) 둘 다 재시도 예산에서 제외한다.
    private static final Set<String> EXCLUDED_FROM_RETRY_BUDGET = Set.of("SKIPPED", "INFRA_FAILED");

    @Override
    public long countRealAttemptsByCoachingSessionIdAndPurpose(UUID coachingSessionId, AiCallPurpose purpose) {
        return springDataAiCallHistoryRepository
                .countByCoachingSessionIdAndPurposeAndRequestStatusNotIn(
                        coachingSessionId, purpose, EXCLUDED_FROM_RETRY_BUDGET
                );
    }
}
