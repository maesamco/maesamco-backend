package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.AiCallPurpose;
import com.maesamco.coaching.domain.entity.CoachingSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallHistoryRepositoryImplTest extends AbstractCoachingRepositoryTest {

    @Autowired
    private SpringDataAiCallHistoryRepository springDataAiCallHistoryRepository;

    @Autowired
    private SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Autowired
    private EntityManager entityManager;

    private AiCallHistoryRepositoryImpl aiCallHistoryRepository;

    @BeforeEach
    void setUp() {
        aiCallHistoryRepository = new AiCallHistoryRepositoryImpl(springDataAiCallHistoryRepository);
    }

    /**
     * coaching_session_id는 실제 FK(Flyway V1 베이스라인)라, 존재하는 CoachingSession을
     * 먼저 저장해야 AiCallHistory 저장이 성공한다.
     */
    private UUID createCoachingSessionId() {
        CoachingSession coachingSession = springDataCoachingSessionRepository.saveAndFlush(
                CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        );
        return coachingSession.getId();
    }

    @Test
    @DisplayName("AI 호출 이력을 저장하면 ID가 채번되고 called_at이 채워진다")
    void save_assignsIdAndCalledAt() {
        // given
        AiCallHistory aiCallHistory = AiCallHistory.create(
                createCoachingSessionId(), AiCallPurpose.HINT, "gpt-4o", "v1",
                "SUCCESS", 1200, 350, null, 0
        );

        // when
        AiCallHistory saved = aiCallHistoryRepository.save(aiCallHistory);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCalledAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 코칭 세션에 대해 여러 번 저장해도 전부 별도 행으로 조회된다(UNIQUE 제약 없음)")
    void findByCoachingSessionId_returnsAllCallsForSameSession() {
        // given
        UUID coachingSessionId = createCoachingSessionId();
        aiCallHistoryRepository.save(AiCallHistory.create(
                coachingSessionId, AiCallPurpose.HINT, "gpt-4o", "v1", "SUCCESS", 1200, 350, null, 0
        ));
        aiCallHistoryRepository.save(AiCallHistory.create(
                coachingSessionId, AiCallPurpose.FOLLOWUP_QUESTION, "gpt-4o", "v1", "SUCCESS", 900, 200, null, 0
        ));
        aiCallHistoryRepository.save(AiCallHistory.create(
                coachingSessionId, AiCallPurpose.FEEDBACK, "gpt-4o", "v1", "FAILED", null, null, "timeout", 1
        ));

        entityManager.flush();
        entityManager.clear();

        // when
        List<AiCallHistory> found = aiCallHistoryRepository.findByCoachingSessionIdOrderByCalledAtAsc(coachingSessionId);

        // then
        assertThat(found).hasSize(3);
        assertThat(found)
                .extracting(AiCallHistory::getPurpose)
                .containsExactlyInAnyOrder(AiCallPurpose.HINT, AiCallPurpose.FOLLOWUP_QUESTION, AiCallPurpose.FEEDBACK);

        AiCallHistory hint = found.stream().filter(h -> h.getPurpose() == AiCallPurpose.HINT).findFirst().orElseThrow();
        assertThat(hint.getModelName()).isEqualTo("gpt-4o");
        assertThat(hint.getPromptVersion()).isEqualTo("v1");
        assertThat(hint.getRequestStatus()).isEqualTo("SUCCESS");
        assertThat(hint.getResponseTimeMs()).isEqualTo(1200);
        assertThat(hint.getTokenUsage()).isEqualTo(350);
        assertThat(hint.getRetryCount()).isEqualTo(0);

        AiCallHistory feedback = found.stream().filter(h -> h.getPurpose() == AiCallPurpose.FEEDBACK).findFirst().orElseThrow();
        assertThat(feedback.getRequestStatus()).isEqualTo("FAILED");
        assertThat(feedback.getFailureReason()).isEqualTo("timeout");
        assertThat(feedback.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 코칭 세션으로 조회하면 빈 목록을 반환한다")
    void findByCoachingSessionId_returnsEmpty_whenNotExists() {
        // when
        List<AiCallHistory> found = aiCallHistoryRepository.findByCoachingSessionIdOrderByCalledAtAsc(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("nullable 필드(응답 시간·토큰 사용량·실패 원인)는 null로 저장하고 조회해도 null을 유지한다")
    void save_allowsNullOptionalFields() {
        // given
        UUID coachingSessionId = createCoachingSessionId();
        aiCallHistoryRepository.save(AiCallHistory.create(
                coachingSessionId, AiCallPurpose.HINT, "gpt-4o", "v1", "PENDING", null, null, null, 0
        ));

        entityManager.flush();
        entityManager.clear();

        // when
        List<AiCallHistory> found = aiCallHistoryRepository.findByCoachingSessionIdOrderByCalledAtAsc(coachingSessionId);

        // then
        assertThat(found).hasSize(1);
        AiCallHistory reloaded = found.get(0);
        assertThat(reloaded.getResponseTimeMs()).isNull();
        assertThat(reloaded.getTokenUsage()).isNull();
        assertThat(reloaded.getFailureReason()).isNull();
        assertThat(reloaded.getPurpose()).isEqualTo(AiCallPurpose.HINT);
        assertThat(reloaded.getModelName()).isEqualTo("gpt-4o");
        assertThat(reloaded.getPromptVersion()).isEqualTo("v1");
        assertThat(reloaded.getRequestStatus()).isEqualTo("PENDING");
        assertThat(reloaded.getRetryCount()).isEqualTo(0);
    }
}
