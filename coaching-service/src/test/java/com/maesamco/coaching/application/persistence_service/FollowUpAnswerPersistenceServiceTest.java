package com.maesamco.coaching.application.persistence_service;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.CoachingSessionStatus;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.ExplanationRepository;
import com.maesamco.coaching.domain.repository.FollowUpAnswerRepository;
import com.maesamco.coaching.domain.repository.FollowUpQuestionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.infrastructure.persistence.CoachingEventOutboxRepositoryImpl;
import com.maesamco.coaching.infrastructure.persistence.CoachingSessionRepositoryImpl;
import com.maesamco.coaching.infrastructure.persistence.ExplanationRepositoryImpl;
import com.maesamco.coaching.infrastructure.persistence.FollowUpAnswerRepositoryImpl;
import com.maesamco.coaching.infrastructure.persistence.FollowUpQuestionRepositoryImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FollowUpAnswerPersistenceService의 원자적 트랜잭션(답변 저장 + 세션 완료 + Outbox 기록)을
 * 실제 Testcontainers PostgreSQL과 Spring이 관리하는 진짜 @Transactional 프록시로 검증한다.
 * FollowUpAnswerRepositoryImplTest 등과 달리 RepositoryImpl을 직접 new하지 않는 이유 —
 * 여기서 검증하려는 건 "예외가 나면 그 트랜잭션 안의 다른 쓰기도 같이 무효화되는지"라
 * 실제 트랜잭션 경계(커밋/롤백)가 필요하고, 그건 Spring이 관리하는 빈이어야 만들어진다
 * (JudgeServiceAdapterTest가 @CircuitBreaker AOP를 검증할 때와 같은 이유 — 직접 new한
 * 객체는 프록시를 안 거친다). DB/Kafka/Redis/AI 벤더 자동 설정 중 이 테스트와 무관한 것만
 * 제외한 슬림 컨텍스트를 쓴다.
 */
@SpringBootTest(classes = {
        FollowUpAnswerPersistenceService.class,
        FollowUpAnswerRepositoryImpl.class,
        CoachingSessionRepositoryImpl.class,
        CoachingEventOutboxRepositoryImpl.class,
        FollowUpQuestionRepositoryImpl.class,
        ExplanationRepositoryImpl.class,
        FollowUpAnswerPersistenceServiceTest.MinimalJpaConfig.class
})
class FollowUpAnswerPersistenceServiceTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        postgres.start();
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {
            KafkaAutoConfiguration.class,
            DataRedisAutoConfiguration.class,
            DataRedisReactiveAutoConfiguration.class,
            AnthropicChatAutoConfiguration.class,
            GoogleGenAiChatAutoConfiguration.class
    })
    @EntityScan(basePackages = "com.maesamco.coaching.domain.entity")
    @EnableJpaRepositories(basePackages = "com.maesamco.coaching.infrastructure.persistence")
    @EnableJpaAuditing
    static class MinimalJpaConfig {
    }

    @Autowired
    private FollowUpAnswerPersistenceService followUpAnswerPersistenceService;

    @Autowired
    private CoachingSessionRepository coachingSessionRepository;

    @Autowired
    private ExplanationRepository explanationRepository;

    @Autowired
    private FollowUpQuestionRepository followUpQuestionRepository;

    @Autowired
    private FollowUpAnswerRepository followUpAnswerRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * follow_up_question_id/coaching_session_id 모두 실제 FK라, FollowUpAnswerRepositoryImplTest
     * 와 동일하게 조상 체인(CoachingSession → Explanation → FollowUpQuestion)을 먼저 저장한다.
     */
    private record Fixture(CoachingSession session, FollowUpQuestion followUpQuestion) {
    }

    private Fixture createFixture() {
        CoachingSession session = coachingSessionRepository.save(
                CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1)
        );
        Explanation explanation =
                explanationRepository.save(Explanation.create(session.getId(), session.getSubmissionId(), "설명 내용"));
        FollowUpQuestion followUpQuestion =
                followUpQuestionRepository.save(FollowUpQuestion.create(explanation.getId(), "질문 내용", null));
        return new Fixture(session, followUpQuestion);
    }

    @Test
    @DisplayName("답변 저장 + 세션 완료 + Outbox 기록이 한 트랜잭션으로 함께 커밋된다")
    void completeWithAnswer_persistsAnswerAndCompletesSessionAndWritesOutbox() {
        // given
        Fixture fixture = createFixture();

        // when
        FollowUpAnswerPersistenceService.FollowUpAnswerCompletionResult result =
                followUpAnswerPersistenceService.completeWithAnswer(
                        fixture.session().getId(), fixture.followUpQuestion().getId(), "답변 내용"
                );

        entityManager.clear();

        // then
        assertThat(result.followUpAnswer().getAnswerText()).isEqualTo("답변 내용");
        assertThat(result.coachingSession().getStatus()).isEqualTo(CoachingSessionStatus.COMPLETED);

        CoachingSession persistedSession = coachingSessionRepository.findById(fixture.session().getId()).orElseThrow();
        assertThat(persistedSession.getStatus()).isEqualTo(CoachingSessionStatus.COMPLETED);
        assertThat(persistedSession.getCompletedAt()).isNotNull();

        assertThat(followUpAnswerRepository.findByFollowUpQuestionId(fixture.followUpQuestion().getId())).isPresent();

        List<CoachingEventOutbox> outboxRows = entityManager
                .createQuery("select o from CoachingEventOutbox o where o.aggregateId = :sessionId", CoachingEventOutbox.class)
                .setParameter("sessionId", fixture.session().getId())
                .getResultList();
        assertThat(outboxRows).hasSize(1);
        CoachingEventOutbox outbox = outboxRows.get(0);
        assertThat(outbox.getEventType()).isEqualTo("CoachingCompleted");
        assertThat(outbox.getPayload().get("coachingId").asString()).isEqualTo(fixture.session().getId().toString());
        assertThat(outbox.getPayload().get("userId").asString()).isEqualTo(fixture.session().getUserId().toString());
        assertThat(outbox.getPayload().get("submissionId").asString()).isEqualTo(fixture.session().getSubmissionId().toString());
        assertThat(outbox.getPayload().get("problemId").asString()).isEqualTo(fixture.session().getProblemId().toString());
        assertThat(outbox.getPayload().has("weakConcepts")).isFalse();
    }

    /**
     * CoachingSession.complete()의 기존 TODO(낙관적 락 없이 check-then-act)를 이 트랜잭션으로
     * 해소한다는 주장의 근거 — 답변 저장 단계에서 UNIQUE(follow_up_question_id) 위반이 나면,
     * 같은 트랜잭션 뒤쪽에 있는 세션 완료·Outbox 기록까지 전부 실행되지 않은 채로 끝나야 한다
     * (원자성). 동시 요청 중 하나가 이미 답변을 저장해둔 상태를 미리 만들어 재현한다.
     */
    @Test
    @DisplayName("답변이 이미 존재하면 예외를 던지고, 세션 완료·Outbox 기록 모두 일어나지 않는다")
    void completeWithAnswer_rollsBackSessionCompletionAndOutbox_whenAnswerAlreadyExists() {
        // given
        Fixture fixture = createFixture();
        followUpAnswerRepository.save(FollowUpAnswer.create(fixture.followUpQuestion().getId(), "먼저 도착한 답변"));

        // when & then
        assertThatThrownBy(() -> followUpAnswerPersistenceService.completeWithAnswer(
                fixture.session().getId(), fixture.followUpQuestion().getId(), "나중에 도착한 답변"
        )).isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FOLLOW_UP_ANSWER_ALREADY_EXISTS)
        );

        entityManager.clear();

        CoachingSession persistedSession = coachingSessionRepository.findById(fixture.session().getId()).orElseThrow();
        assertThat(persistedSession.getStatus()).isEqualTo(CoachingSessionStatus.IN_PROGRESS);
        assertThat(persistedSession.getCompletedAt()).isNull();

        List<CoachingEventOutbox> outboxRows = entityManager
                .createQuery("select o from CoachingEventOutbox o where o.aggregateId = :sessionId", CoachingEventOutbox.class)
                .setParameter("sessionId", fixture.session().getId())
                .getResultList();
        assertThat(outboxRows).isEmpty();
    }

    private FollowUpQuestion createSecondFollowUpQuestion(CoachingSession session) {
        Explanation explanation = explanationRepository.save(
                Explanation.create(session.getId(), UUID.randomUUID(), "설명 내용 2")
        );
        return followUpQuestionRepository.save(FollowUpQuestion.create(explanation.getId(), "질문 내용 2", null));
    }

    /**
     * PR #98 자가 리뷰(용현님 P1) 대응 — 한 세션에 서로 다른 역질문이 여러 개 있을 수
     * 있는데(재도전 시 새 설명 등록, 이슈 #84), 첫 번째 역질문 답변으로 세션이 이미
     * COMPLETED된 뒤 두 번째 역질문에 답하면 예전엔 complete()가 ALREADY_COMPLETED를
     * 던져 트랜잭션 전체가 롤백되면서 방금 저장한 정당한 답변까지 사라졌다. 이제는 답변만
     * 저장되고 세션 재완료·Outbox 재발행은 건너뛴다.
     */
    @Test
    @DisplayName("이미 완료된 세션의 다른 역질문에 답하면 답변만 저장되고 세션 재완료·Outbox 재발행은 없다")
    void completeWithAnswer_savesAnswerOnly_whenSessionAlreadyCompletedByAnotherQuestion() {
        // given
        Fixture fixture = createFixture();
        followUpAnswerPersistenceService.completeWithAnswer(
                fixture.session().getId(), fixture.followUpQuestion().getId(), "첫 번째 답변"
        );
        entityManager.clear();
        FollowUpQuestion secondQuestion = createSecondFollowUpQuestion(fixture.session());

        // when
        FollowUpAnswerPersistenceService.FollowUpAnswerCompletionResult result =
                followUpAnswerPersistenceService.completeWithAnswer(
                        fixture.session().getId(), secondQuestion.getId(), "두 번째 답변"
                );

        entityManager.clear();

        // then
        assertThat(result.followUpAnswer().getAnswerText()).isEqualTo("두 번째 답변");
        assertThat(result.coachingSession().getStatus()).isEqualTo(CoachingSessionStatus.COMPLETED);
        assertThat(followUpAnswerRepository.findByFollowUpQuestionId(secondQuestion.getId())).isPresent();

        List<CoachingEventOutbox> outboxRows = entityManager
                .createQuery("select o from CoachingEventOutbox o where o.aggregateId = :sessionId", CoachingEventOutbox.class)
                .setParameter("sessionId", fixture.session().getId())
                .getResultList();
        assertThat(outboxRows).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 코칭 세션 ID면 COACHING_SESSION_NOT_FOUND를 던지고 답변도 저장하지 않는다")
    void completeWithAnswer_throwsCoachingSessionNotFound_whenSessionDoesNotExist() {
        // given
        Fixture fixture = createFixture();
        UUID nonExistentSessionId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> followUpAnswerPersistenceService.completeWithAnswer(
                nonExistentSessionId, fixture.followUpQuestion().getId(), "답변 내용"
        )).isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COACHING_SESSION_NOT_FOUND)
        );

        assertThat(followUpAnswerRepository.findByFollowUpQuestionId(fixture.followUpQuestion().getId())).isEmpty();
    }
}
