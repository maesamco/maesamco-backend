package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.Explanation;
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
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableJpaAuditing
@Testcontainers
class ExplanationRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private SpringDataExplanationRepository springDataExplanationRepository;

    @Autowired
    private EntityManager entityManager;

    private ExplanationRepositoryImpl explanationRepository;

    @BeforeEach
    void setUp() {
        explanationRepository = new ExplanationRepositoryImpl(springDataExplanationRepository);
    }

    @Test
    @DisplayName("설명을 저장하면 ID와 생성 시각이 채워진다")
    void save_assignsIdAndCreatedAt() {
        // given
        Explanation explanation = Explanation.create(UUID.randomUUID(), "내용");

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
        UUID coachingSessionId = UUID.randomUUID();
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
        UUID coachingSessionId = UUID.randomUUID();
        explanationRepository.save(Explanation.create(coachingSessionId, "1차 설명"));

        Explanation duplicate = Explanation.create(coachingSessionId, "2차 설명");

        // when & then
        assertThatThrownBy(() -> explanationRepository.save(duplicate))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXPLANATION_ALREADY_EXISTS)
                );
    }
}
