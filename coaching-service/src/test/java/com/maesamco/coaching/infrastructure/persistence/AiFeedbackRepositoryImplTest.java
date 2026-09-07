package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.AiFeedback;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이 테스트는 JSONB 컬럼(JsonNode 매핑)이 실제 PostgreSQL에 왕복되는지 검증하는 첫 사례다.
 */
class AiFeedbackRepositoryImplTest extends AbstractCoachingRepositoryTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private SpringDataAiFeedbackRepository springDataAiFeedbackRepository;

    @Autowired
    private SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Autowired
    private EntityManager entityManager;

    private AiFeedbackRepositoryImpl aiFeedbackRepository;

    @BeforeEach
    void setUp() {
        aiFeedbackRepository = new AiFeedbackRepositoryImpl(springDataAiFeedbackRepository);
    }

    /**
     * coaching_session_id는 실제 FK(Flyway V1 베이스라인)라, 존재하는 CoachingSession을
     * 먼저 저장해야 AiFeedback 저장이 성공한다.
     */
    private UUID createCoachingSessionId() {
        CoachingSession coachingSession = springDataCoachingSessionRepository.saveAndFlush(
                CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1)
        );
        return coachingSession.getId();
    }

    @Test
    @DisplayName("AI 피드백을 저장하면 JSONB 필드가 실제 DB를 왕복해도 그대로 유지된다")
    void save_andFindByCoachingSessionId_roundTripsJsonFields() throws Exception {
        // given
        UUID coachingSessionId = createCoachingSessionId();
        JsonNode understoodConcepts = jsonMapper.readTree("[\"반복문\", \"조건문\"]");
        JsonNode explanationGaps = jsonMapper.readTree("[\"배열 인덱스 경계값\"]");
        JsonNode weakConcepts = jsonMapper.readTree("[\"재귀\"]");
        JsonNode syntaxToImprove = jsonMapper.readTree("[\"for-each 문법\"]");
        JsonNode recommendedProblems = jsonMapper.readTree(
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
        UUID coachingSessionId = createCoachingSessionId();
        JsonNode required = jsonMapper.readTree("[]");

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
    @DisplayName("JSONB 필드는 실제로 jsonb 컬럼 타입으로, created_at은 timestamptz로 생성된다")
    void schema_usesJsonbAndTimestamptzColumnTypes() {
        // when
        String understoodConceptsType = columnDataType("understood_concepts");
        String createdAtType = columnDataType("created_at");

        // then
        assertThat(understoodConceptsType).isEqualTo("jsonb");
        assertThat(createdAtType).isEqualTo("timestamp with time zone");
    }

    @SuppressWarnings("unchecked")
    private String columnDataType(String columnName) {
        return (String) entityManager.createNativeQuery(
                        "SELECT data_type FROM information_schema.columns "
                                + "WHERE table_schema = 'coaching_schema' "
                                + "AND table_name = 'p_ai_feedbacks' AND column_name = :columnName"
                )
                .setParameter("columnName", columnName)
                .getSingleResult();
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
        JsonNode required = jsonMapper.readTree("[]");
        UUID coachingSessionId = createCoachingSessionId();
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

    @Test
    @DisplayName("존재하지 않는 코칭 세션으로 AI 피드백을 저장하면 FK 위반으로 실패한다(AI_FEEDBACK_ALREADY_EXISTS로 잘못 변환되지 않음)")
    void save_throwsRawExceptionWhenCoachingSessionDoesNotExist() throws Exception {
        // given
        JsonNode required = jsonMapper.readTree("[]");
        AiFeedback aiFeedback =
                AiFeedback.create(UUID.randomUUID(), required, required, required, null, null, null);

        // when & then
        assertThatThrownBy(() -> aiFeedbackRepository.save(aiFeedback))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(BusinessException.class);
    }
}
