package com.maesamco.coaching.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maesamco.coaching.domain.entity.AiFeedback;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 팀 컨벤션 18절 — Repository 통합 테스트는 H2가 아니라 Testcontainers 실제 PostgreSQL로 검증한다.
 *
 * ⚠️ 마이그레이션 도구는 Flyway로 확정됐지만(팀 컨벤션 16절, 이슈 #10) 아직 실제 마이그레이션
 *    스크립트가 도입되기 전이라, 운영 스크립트 대신 테스트 전용 ddl-auto=create-drop으로
 *    coaching_schema를 직접 생성해서 검증한다.
 *
 * 이 테스트는 JSONB 컬럼(JsonNode 매핑)이 실제 PostgreSQL에 왕복되는지 검증하는 첫 사례다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableJpaAuditing
@Testcontainers
class AiFeedbackRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SpringDataAiFeedbackRepository springDataAiFeedbackRepository;

    @Autowired
    private EntityManager entityManager;

    private AiFeedbackRepositoryImpl aiFeedbackRepository;

    @BeforeEach
    void setUp() {
        aiFeedbackRepository = new AiFeedbackRepositoryImpl(springDataAiFeedbackRepository);
    }

    @Test
    @DisplayName("AI 피드백을 저장하면 JSONB 필드가 실제 DB를 왕복해도 그대로 유지된다")
    void save_andFindByCoachingSessionId_roundTripsJsonFields() throws Exception {
        // given
        UUID coachingSessionId = UUID.randomUUID();
        JsonNode understoodConcepts = objectMapper.readTree("[\"반복문\", \"조건문\"]");
        JsonNode explanationGaps = objectMapper.readTree("[\"배열 인덱스 경계값\"]");
        JsonNode weakConcepts = objectMapper.readTree("[\"재귀\"]");
        JsonNode syntaxToImprove = objectMapper.readTree("[\"for-each 문법\"]");
        JsonNode recommendedProblems = objectMapper.readTree(
                "[\"" + UUID.randomUUID() + "\"]"
        );

        AiFeedback aiFeedback = AiFeedback.create(
                coachingSessionId, understoodConcepts, explanationGaps, weakConcepts,
                syntaxToImprove, recommendedProblems, "다음엔 재귀를 복습하세요."
        );

        // when
        aiFeedbackRepository.save(aiFeedback);
        entityManager.flush();
        entityManager.clear();

        Optional<AiFeedback> found = aiFeedbackRepository.findByCoachingSessionId(coachingSessionId);

        // then
        assertThat(found).isPresent();
        AiFeedback foundFeedback = found.get();
        assertThat(foundFeedback.getUnderstoodConcepts()).isEqualTo(understoodConcepts);
        assertThat(foundFeedback.getExplanationGaps()).isEqualTo(explanationGaps);
        assertThat(foundFeedback.getWeakConcepts()).isEqualTo(weakConcepts);
        assertThat(foundFeedback.getSyntaxToImprove()).isEqualTo(syntaxToImprove);
        assertThat(foundFeedback.getRecommendedProblems()).isEqualTo(recommendedProblems);
        assertThat(foundFeedback.getNextDirection()).isEqualTo("다음엔 재귀를 복습하세요.");
        assertThat(foundFeedback.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("nullable JSONB 필드는 null로 저장하고 조회해도 null을 유지한다")
    void save_allowsNullOptionalJsonFields() throws Exception {
        // given
        UUID coachingSessionId = UUID.randomUUID();
        JsonNode required = objectMapper.readTree("[]");

        aiFeedbackRepository.save(
                AiFeedback.create(coachingSessionId, required, required, required, null, null, null)
        );

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<AiFeedback> found = aiFeedbackRepository.findByCoachingSessionId(coachingSessionId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getSyntaxToImprove()).isNull();
        assertThat(found.get().getRecommendedProblems()).isNull();
        assertThat(found.get().getNextDirection()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 세션으로 조회하면 빈 결과를 반환한다")
    void findByCoachingSessionId_returnsEmpty_whenNotExists() {
        // when
        Optional<AiFeedback> found = aiFeedbackRepository.findByCoachingSessionId(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은 세션에 AI 피드백을 두 번 저장하면 AI_FEEDBACK_ALREADY_EXISTS(409)로 실패한다")
    void save_throwsWhenSessionAlreadyExists() throws Exception {
        // given
        JsonNode required = objectMapper.readTree("[]");
        UUID coachingSessionId = UUID.randomUUID();
        aiFeedbackRepository.save(
                AiFeedback.create(coachingSessionId, required, required, required, null, null, null)
        );

        AiFeedback duplicate =
                AiFeedback.create(coachingSessionId, required, required, required, null, null, null);

        // when & then
        assertThatThrownBy(() -> aiFeedbackRepository.save(duplicate))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_FEEDBACK_ALREADY_EXISTS)
                );
    }
}
