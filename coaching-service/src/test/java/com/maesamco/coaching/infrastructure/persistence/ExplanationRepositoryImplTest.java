package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExplanationRepositoryImplTest extends AbstractCoachingRepositoryTest {

    @Autowired
    private SpringDataExplanationRepository springDataExplanationRepository;

    @Autowired
    private SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Autowired
    private EntityManager entityManager;

    private ExplanationRepositoryImpl explanationRepository;

    @BeforeEach
    void setUp() {
        explanationRepository = new ExplanationRepositoryImpl(springDataExplanationRepository);
    }

    /**
     * coaching_session_id는 실제 FK(Flyway V1 베이스라인)라, 존재하는 CoachingSession을
     * 먼저 저장해야 Explanation 저장이 성공한다.
     */
    private UUID createCoachingSessionId() {
        CoachingSession coachingSession = springDataCoachingSessionRepository.saveAndFlush(
                CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        );
        return coachingSession.getId();
    }

    @Test
    @DisplayName("설명을 저장하면 ID와 생성 시각이 채워진다")
    void save_assignsIdAndCreatedAt() {
        // given
        Explanation explanation = Explanation.create(createCoachingSessionId(), "내용");

        // when
        Explanation saved = explanationRepository.save(explanation);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("코칭 세션 ID로 설명을 조회할 수 있다")
    void findByCoachingSessionId_returnsExplanation() {
        // given
        UUID coachingSessionId = createCoachingSessionId();
        explanationRepository.save(Explanation.create(coachingSessionId, "내용"));

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<Explanation> found = explanationRepository.findByCoachingSessionId(coachingSessionId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("내용");
    }

    @Test
    @DisplayName("존재하지 않는 세션으로 조회하면 빈 결과를 반환한다")
    void findByCoachingSessionId_returnsEmpty_whenNotExists() {
        // when
        Optional<Explanation> found = explanationRepository.findByCoachingSessionId(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은 세션에 설명을 두 번 저장하면 EXPLANATION_ALREADY_EXISTS(409)로 실패한다")
    void save_throwsWhenSessionAlreadyExists() {
        // given
        UUID coachingSessionId = createCoachingSessionId();
        explanationRepository.save(Explanation.create(coachingSessionId, "1차 설명"));

        Explanation duplicate = Explanation.create(coachingSessionId, "2차 설명");

        // when & then
        assertThatThrownBy(() -> explanationRepository.save(duplicate))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXPLANATION_ALREADY_EXISTS)
                );
    }

    @Test
    @DisplayName("존재하지 않는 코칭 세션으로 설명을 저장하면 FK 위반으로 실패한다(EXPLANATION_ALREADY_EXISTS로 잘못 변환되지 않음)")
    void save_throwsRawExceptionWhenCoachingSessionDoesNotExist() {
        // given
        Explanation explanation = Explanation.create(UUID.randomUUID(), "내용");

        // when & then
        assertThatThrownBy(() -> explanationRepository.save(explanation))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(BusinessException.class);
    }
}
