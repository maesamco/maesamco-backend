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
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 팀 컨벤션 18절 — Repository 통합 테스트는 H2가 아니라 Testcontainers 실제 PostgreSQL로 검증한다.
 *
 * 마이그레이션 도구는 Flyway로 확정됐고(팀 컨벤션 16절, 이슈 #10) V1 베이스라인 스크립트도
 * 이미 도입돼 있다(PR #29). Hibernate가 스키마를 직접 만드는 대신 이 실제 마이그레이션
 * 스크립트로 생성된 스키마를 ddl-auto=validate로 검증하도록 해서, 엔티티 매핑이 실제 운영
 * 스키마와 정확히 일치하는지까지 함께 확인한다. @DataJpaTest는 기본적으로 FlywayAutoConfiguration을
 * 포함하지 않아 @ImportAutoConfiguration으로 명시적으로 가져와야 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@EnableJpaAuditing
@Testcontainers
class FollowUpAnswerRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

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
                Explanation.create(coachingSession.getId(), "설명 내용")
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
}
