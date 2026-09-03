package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
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

class FollowUpQuestionRepositoryImplTest extends AbstractCoachingRepositoryTest {

    @Autowired
    private SpringDataFollowUpQuestionRepository springDataFollowUpQuestionRepository;

    @Autowired
    private SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Autowired
    private SpringDataExplanationRepository springDataExplanationRepository;

    @Autowired
    private EntityManager entityManager;

    private FollowUpQuestionRepositoryImpl followUpQuestionRepository;

    @BeforeEach
    void setUp() {
        followUpQuestionRepository = new FollowUpQuestionRepositoryImpl(springDataFollowUpQuestionRepository);
    }

    /**
     * explanation_id는 실제 FK(Flyway V1 베이스라인)라, 존재하는 Explanation(그리고 그 부모
     * CoachingSession)을 먼저 저장해야 FollowUpQuestion 저장이 성공한다.
     */
    private UUID createExplanationId() {
        CoachingSession coachingSession = springDataCoachingSessionRepository.saveAndFlush(
                CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        );
        Explanation explanation = springDataExplanationRepository.saveAndFlush(
                Explanation.create(coachingSession.getId(), UUID.randomUUID(), "설명 내용")
        );
        return explanation.getId();
    }

    @Test
    @DisplayName("역질문을 저장하면 ID와 생성 시각이 채워진다")
    void save_assignsIdAndCreatedAt() {
        // given
        FollowUpQuestion followUpQuestion =
                FollowUpQuestion.create(createExplanationId(), "질문 내용", "선택이유");

        // when
        FollowUpQuestion saved = followUpQuestionRepository.save(followUpQuestion);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("설명 ID로 역질문을 조회할 수 있다")
    void findByExplanationId_returnsFollowUpQuestion() {
        // given
        UUID explanationId = createExplanationId();
        followUpQuestionRepository.save(FollowUpQuestion.create(explanationId, "질문 내용", "선택이유"));

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<FollowUpQuestion> found = followUpQuestionRepository.findByExplanationId(explanationId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getQuestionText()).isEqualTo("질문 내용");
        assertThat(found.get().getCategory()).isEqualTo("선택이유");
    }

    @Test
    @DisplayName("존재하지 않는 설명으로 조회하면 빈 결과를 반환한다")
    void findByExplanationId_returnsEmpty_whenNotExists() {
        // when
        Optional<FollowUpQuestion> found =
                followUpQuestionRepository.findByExplanationId(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은 설명에 역질문을 두 번 저장하면 FOLLOW_UP_QUESTION_ALREADY_EXISTS(409)로 실패한다")
    void save_throwsWhenExplanationAlreadyExists() {
        // given
        UUID explanationId = createExplanationId();
        followUpQuestionRepository.save(FollowUpQuestion.create(explanationId, "1차 질문", null));

        FollowUpQuestion duplicate = FollowUpQuestion.create(explanationId, "2차 질문", null);

        // when & then
        assertThatThrownBy(() -> followUpQuestionRepository.save(duplicate))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FOLLOW_UP_QUESTION_ALREADY_EXISTS)
                );
    }

    @Test
    @DisplayName("존재하지 않는 설명으로 역질문을 저장하면 FK 위반으로 실패한다(FOLLOW_UP_QUESTION_ALREADY_EXISTS로 잘못 변환되지 않음)")
    void save_throwsRawExceptionWhenExplanationDoesNotExist() {
        // given
        FollowUpQuestion followUpQuestion = FollowUpQuestion.create(UUID.randomUUID(), "질문 내용", null);

        // when & then
        assertThatThrownBy(() -> followUpQuestionRepository.save(followUpQuestion))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(BusinessException.class);
    }
}
