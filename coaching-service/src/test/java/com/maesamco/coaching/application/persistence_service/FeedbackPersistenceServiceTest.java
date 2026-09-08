package com.maesamco.coaching.application.persistence_service;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.AiFeedbackRepository;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.WeakConceptRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.infrastructure.persistence.AiCallHistoryRepositoryImpl;
import com.maesamco.coaching.infrastructure.persistence.AiFeedbackRepositoryImpl;
import com.maesamco.coaching.infrastructure.persistence.CoachingSessionRepositoryImpl;
import com.maesamco.coaching.infrastructure.persistence.WeakConceptRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FeedbackPersistenceService.saveFeedback()의 원자성(AiCallHistory + AiFeedback +
 * WeakConcept가 한 트랜잭션)을 실제 Testcontainers PostgreSQL과 Spring이 관리하는 진짜
 * @Transactional 프록시로 검증한다 — FollowUpAnswerPersistenceServiceTest와 동일한 이유
 * (직접 new한 객체는 트랜잭션 프록시를 안 거친다).
 */
@SpringBootTest(classes = {
        FeedbackPersistenceService.class,
        AiCallHistoryRepositoryImpl.class,
        AiFeedbackRepositoryImpl.class,
        WeakConceptRepositoryImpl.class,
        CoachingSessionRepositoryImpl.class,
        FeedbackPersistenceServiceTest.MinimalJpaConfig.class
})
class FeedbackPersistenceServiceTest {

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
    private FeedbackPersistenceService feedbackPersistenceService;

    @Autowired
    private CoachingSessionRepository coachingSessionRepository;

    @Autowired
    private AiCallHistoryRepository aiCallHistoryRepository;

    @Autowired
    private AiFeedbackRepository aiFeedbackRepository;

    @Autowired
    private WeakConceptRepository weakConceptRepository;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private JsonNode array(String... values) {
        var node = JSON_MAPPER.createArrayNode();
        for (String value : values) {
            node.add(value);
        }
        return node;
    }

    private CoachingSession createSession(UUID userId) {
        return coachingSessionRepository.save(
                CoachingSession.create(UUID.randomUUID(), userId, UUID.randomUUID(), 1)
        );
    }

    @Test
    @DisplayName("AiCallHistory(SUCCESS) + AiFeedback + 신규 WeakConcept가 한 번에 저장된다")
    void saveFeedback_persistsHistoryFeedbackAndNewWeakConcept() {
        UUID userId = UUID.randomUUID();
        CoachingSession session = createSession(userId);

        feedbackPersistenceService.saveFeedback(
                session.getId(), userId, "claude-sonnet-5", "feedback-v1", 30,
                array("반복문"), array("경계값"), array("재귀"), array("변수명"), array("이분탐색"), "재귀를 복습하세요"
        );

        assertThat(aiFeedbackRepository.findByCoachingSessionId(session.getId())).isPresent();
        assertThat(aiCallHistoryRepository.findByCoachingSessionIdOrderByCalledAtAsc(session.getId()))
                .hasSize(1)
                .allSatisfy(h -> assertThat(h.getRequestStatus()).isEqualTo("SUCCESS"));
        assertThat(weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀"))
                .isPresent()
                .get()
                .satisfies(w -> assertThat(w.getOccurrenceCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("이미 있는 WeakConcept면 새로 만들지 않고 발견 횟수만 갱신한다")
    void saveFeedback_updatesExistingWeakConcept() {
        UUID userId = UUID.randomUUID();
        CoachingSession session = createSession(userId);
        weakConceptRepository.save(WeakConcept.create(userId, "재귀"));

        feedbackPersistenceService.saveFeedback(
                session.getId(), userId, "claude-sonnet-5", "feedback-v1", 30,
                array("반복문"), array(), array("재귀"), array(), array(), null
        );

        assertThat(weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀"))
                .get()
                .satisfies(w -> assertThat(w.getOccurrenceCount()).isEqualTo(2));
    }

    /**
     * PR #98 리뷰(용현님 P1) 대응의 핵심 검증 — 저장 구간 중간에 실패하면 이미 flush된
     * AiCallHistory/AiFeedback까지 전부 롤백돼야 "SUCCESS로 기록됐는데 실제로는 일부만
     * 저장됨" 상태가 안 생긴다. 목(mock)이 아니라 실제 검증 오류(태그 50자 초과,
     * Validate.requireText가 던짐)로 트랜잭션 중간 실패를 재현한다.
     */
    @Test
    @DisplayName("WeakConcept 저장 중 검증 실패로 예외가 나면 AiCallHistory·AiFeedback까지 전부 롤백된다")
    void saveFeedback_rollsBackEverything_whenWeakConceptSaveFails() {
        UUID userId = UUID.randomUUID();
        CoachingSession session = createSession(userId);
        String tooLongTag = "가".repeat(51);

        assertThatThrownBy(() -> feedbackPersistenceService.saveFeedback(
                session.getId(), userId, "claude-sonnet-5", "feedback-v1", 30,
                array("반복문"), array(), array(tooLongTag), array(), array(), null
        )).isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
        );

        assertThat(aiFeedbackRepository.findByCoachingSessionId(session.getId())).isEmpty();
        assertThat(aiCallHistoryRepository.findByCoachingSessionIdOrderByCalledAtAsc(session.getId())).isEmpty();
        assertThat(weakConceptRepository.findByUserIdAndConceptTag(userId, tooLongTag)).isEmpty();
    }
}
