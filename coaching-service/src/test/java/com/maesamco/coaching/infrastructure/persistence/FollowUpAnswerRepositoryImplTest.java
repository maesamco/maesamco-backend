package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
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

class FollowUpAnswerRepositoryImplTest extends AbstractCoachingRepositoryTest {

    @Autowired
    private SpringDataFollowUpAnswerRepository springDataFollowUpAnswerRepository;

    @Autowired
    private SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Autowired
    private SpringDataExplanationRepository springDataExplanationRepository;

    @Autowired
    private SpringDataFollowUpQuestionRepository springDataFollowUpQuestionRepository;

    @Autowired
    private EntityManager entityManager;

    private FollowUpAnswerRepositoryImpl followUpAnswerRepository;

    @BeforeEach
    void setUp() {
        followUpAnswerRepository = new FollowUpAnswerRepositoryImpl(springDataFollowUpAnswerRepository);
    }

    /**
     * follow_up_question_id는 실제 FK(Flyway V1 베이스라인)라, 존재하는 FollowUpQuestion과
     * 그 조상(Explanation, CoachingSession)을 먼저 저장해야 FollowUpAnswer 저장이 성공한다.
     */
    private UUID createFollowUpQuestionId() {
        CoachingSession coachingSession = springDataCoachingSessionRepository.saveAndFlush(
                CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        );
        Explanation explanation = springDataExplanationRepository.saveAndFlush(
                Explanation.create(coachingSession.getId(), UUID.randomUUID(), "설명 내용")
        );
        FollowUpQuestion followUpQuestion = springDataFollowUpQuestionRepository.saveAndFlush(
                FollowUpQuestion.create(explanation.getId(), "질문 내용", null)
        );
        return followUpQuestion.getId();
    }

    @Test
    @DisplayName("답변을 저장하면 ID와 답변 시각이 채워진다")
    void save_assignsIdAndAnsweredAt() {
        // given
        FollowUpAnswer followUpAnswer = FollowUpAnswer.create(createFollowUpQuestionId(), "답변 내용");

        // when
        FollowUpAnswer saved = followUpAnswerRepository.save(followUpAnswer);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAnsweredAt()).isNotNull();
    }

    @Test
    @DisplayName("역질문 ID로 답변을 조회할 수 있다")
    void findByFollowUpQuestionId_returnsFollowUpAnswer() {
        // given
        UUID followUpQuestionId = createFollowUpQuestionId();
        followUpAnswerRepository.save(FollowUpAnswer.create(followUpQuestionId, "답변 내용"));

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<FollowUpAnswer> found = followUpAnswerRepository.findByFollowUpQuestionId(followUpQuestionId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getAnswerText()).isEqualTo("답변 내용");
    }

    @Test
    @DisplayName("존재하지 않는 역질문으로 조회하면 빈 결과를 반환한다(미답변 상태)")
    void findByFollowUpQuestionId_returnsEmpty_whenNotExists() {
        // when
        Optional<FollowUpAnswer> found =
                followUpAnswerRepository.findByFollowUpQuestionId(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은 역질문에 답변을 두 번 저장하면 FOLLOW_UP_ANSWER_ALREADY_EXISTS(409)로 실패한다")
    void save_throwsWhenFollowUpQuestionAlreadyExists() {
        // given
        UUID followUpQuestionId = createFollowUpQuestionId();
        followUpAnswerRepository.save(FollowUpAnswer.create(followUpQuestionId, "1차 답변"));

        FollowUpAnswer duplicate = FollowUpAnswer.create(followUpQuestionId, "2차 답변");

        // when & then
        assertThatThrownBy(() -> followUpAnswerRepository.save(duplicate))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FOLLOW_UP_ANSWER_ALREADY_EXISTS)
                );
    }

    @Test
    @DisplayName("존재하지 않는 역질문으로 답변을 저장하면 FK 위반으로 실패한다(FOLLOW_UP_ANSWER_ALREADY_EXISTS로 잘못 변환되지 않음)")
    void save_throwsRawExceptionWhenFollowUpQuestionDoesNotExist() {
        // given
        FollowUpAnswer followUpAnswer = FollowUpAnswer.create(UUID.randomUUID(), "답변 내용");

        // when & then
        assertThatThrownBy(() -> followUpAnswerRepository.save(followUpAnswer))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(BusinessException.class);
    }
}
