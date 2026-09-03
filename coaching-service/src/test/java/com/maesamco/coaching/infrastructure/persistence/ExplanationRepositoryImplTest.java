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
        Explanation explanation = Explanation.create(createCoachingSessionId(), UUID.randomUUID(), "내용");

        // when
        Explanation saved = explanationRepository.save(explanation);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("제출 ID로 설명을 조회할 수 있다")
    void findBySubmissionId_returnsExplanation() {
        // given
        UUID submissionId = UUID.randomUUID();
        explanationRepository.save(Explanation.create(createCoachingSessionId(), submissionId, "내용"));

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<Explanation> found = explanationRepository.findBySubmissionId(submissionId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("내용");
    }

    @Test
    @DisplayName("존재하지 않는 제출로 조회하면 빈 결과를 반환한다")
    void findBySubmissionId_returnsEmpty_whenNotExists() {
        // when
        Optional<Explanation> found = explanationRepository.findBySubmissionId(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은 제출에 설명을 두 번 저장하면 EXPLANATION_ALREADY_EXISTS(409)로 실패한다")
    void save_throwsWhenSubmissionAlreadyExists() {
        // given
        UUID coachingSessionId = createCoachingSessionId();
        UUID submissionId = UUID.randomUUID();
        explanationRepository.save(Explanation.create(coachingSessionId, submissionId, "1차 설명"));

        Explanation duplicate = Explanation.create(coachingSessionId, submissionId, "2차 설명");

        // when & then
        assertThatThrownBy(() -> explanationRepository.save(duplicate))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXPLANATION_ALREADY_EXISTS)
                );
    }

    /**
     * 이슈 #84 결정 2의 핵심 동작 — 코칭 세션은 문제당 평생 1개(V5)로 재사용되지만,
     * 같은 세션에 대해 서로 다른 제출(재도전)이라면 설명은 각각 따로 등록될 수 있어야
     * 한다(V6, UNIQUE(submission_id)로 변경돼 더 이상 세션 단위로 막히지 않음).
     */
    @Test
    @DisplayName("같은 코칭 세션이라도 제출 ID가 다르면 설명을 각각 등록할 수 있다")
    void save_allowsMultipleExplanationsForSameSession_withDifferentSubmissionIds() {
        // given
        UUID coachingSessionId = createCoachingSessionId();

        // when
        Explanation first = explanationRepository.save(
                Explanation.create(coachingSessionId, UUID.randomUUID(), "1차 시도 설명")
        );
        Explanation second = explanationRepository.save(
                Explanation.create(coachingSessionId, UUID.randomUUID(), "재도전 설명")
        );

        // then
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getCoachingSessionId()).isEqualTo(second.getCoachingSessionId());
        assertThat(first.getSubmissionId()).isNotEqualTo(second.getSubmissionId());
    }

    @Test
    @DisplayName("존재하지 않는 코칭 세션으로 설명을 저장하면 FK 위반으로 실패한다(EXPLANATION_ALREADY_EXISTS로 잘못 변환되지 않음)")
    void save_throwsRawExceptionWhenCoachingSessionDoesNotExist() {
        // given
        Explanation explanation = Explanation.create(UUID.randomUUID(), UUID.randomUUID(), "내용");

        // when & then
        assertThatThrownBy(() -> explanationRepository.save(explanation))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(BusinessException.class);
    }
}
