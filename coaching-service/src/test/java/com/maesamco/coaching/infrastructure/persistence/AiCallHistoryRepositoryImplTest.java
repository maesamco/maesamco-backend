package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.AiCallPurpose;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 팀 컨벤션 18절 — Repository 통합 테스트는 H2가 아니라 Testcontainers 실제 PostgreSQL로 검증한다.
 *
 * ⚠️ 마이그레이션 도구는 Flyway로 확정됐지만(팀 컨벤션 16절, 이슈 #10) 아직 실제 마이그레이션
 *    스크립트가 도입되기 전이라, 운영 스크립트 대신 테스트 전용 ddl-auto=create-drop으로
 *    coaching_schema를 직접 생성해서 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableJpaAuditing
@Testcontainers
class AiCallHistoryRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private SpringDataAiCallHistoryRepository springDataAiCallHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    private AiCallHistoryRepositoryImpl aiCallHistoryRepository;

    @BeforeEach
    void setUp() {
        aiCallHistoryRepository = new AiCallHistoryRepositoryImpl(springDataAiCallHistoryRepository);
    }

    @Test
    @DisplayName("AI 호출 이력을 저장하면 ID가 채번되고 called_at이 채워진다")
    void save_assignsIdAndCalledAt() {
        // given
        AiCallHistory aiCallHistory = AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, "gpt-4o", "v1",
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
        UUID coachingSessionId = UUID.randomUUID();
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
        List<AiCallHistory> found = aiCallHistoryRepository.findByCoachingSessionId(coachingSessionId);

        // then
        assertThat(found).hasSize(3);
        assertThat(found)
                .extracting(AiCallHistory::getPurpose)
                .containsExactlyInAnyOrder(AiCallPurpose.HINT, AiCallPurpose.FOLLOWUP_QUESTION, AiCallPurpose.FEEDBACK);
    }

    @Test
    @DisplayName("존재하지 않는 코칭 세션으로 조회하면 빈 목록을 반환한다")
    void findByCoachingSessionId_returnsEmpty_whenNotExists() {
        // when
        List<AiCallHistory> found = aiCallHistoryRepository.findByCoachingSessionId(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("nullable 필드(응답 시간·토큰 사용량·실패 원인)는 null로 저장하고 조회해도 null을 유지한다")
    void save_allowsNullOptionalFields() {
        // given
        UUID coachingSessionId = UUID.randomUUID();
        aiCallHistoryRepository.save(AiCallHistory.create(
                coachingSessionId, AiCallPurpose.HINT, "gpt-4o", "v1", "PENDING", null, null, null, 0
        ));

        entityManager.flush();
        entityManager.clear();

        // when
        List<AiCallHistory> found = aiCallHistoryRepository.findByCoachingSessionId(coachingSessionId);

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
