package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.CoachingSessionStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
 *    coaching_schema를 직접 생성해서 검증한다. 마이그레이션 스크립트가 도입되면 그 스크립트
 *    기준으로 교체해야 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableJpaAuditing
@Testcontainers
class CoachingSessionRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Autowired
    private EntityManager entityManager;

    private CoachingSessionRepositoryImpl coachingSessionRepository;

    @BeforeEach
    void setUp() {
        coachingSessionRepository = new CoachingSessionRepositoryImpl(springDataCoachingSessionRepository);
    }

    @Test
    @DisplayName("코칭 세션을 저장하면 ID가 채번되고 IN_PROGRESS 상태·생성 시각이 채워진다")
    void save_assignsIdAndDefaults() {
        // given
        CoachingSession coachingSession = CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        // when
        CoachingSession saved = coachingSessionRepository.save(coachingSession);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(CoachingSessionStatus.IN_PROGRESS);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("submissionId로 코칭 세션을 조회할 수 있다")
    void findBySubmissionId_returnsSession() {
        // given
        UUID submissionId = UUID.randomUUID();
        coachingSessionRepository.save(CoachingSession.create(submissionId, UUID.randomUUID(), UUID.randomUUID()));

        /*
         * flush로 INSERT를 실제로 반영하고, clear로 영속성 컨텍스트(1차 캐시)를 비운다.
         * 비우지 않으면 아래 조회가 방금 저장한 Java 객체를 그대로 돌려줄 수 있어서,
         * 컬럼 매핑이 실제로 잘못돼 있어도 테스트가 못 잡아낼 수 있다.
         */
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<CoachingSession> found = coachingSessionRepository.findBySubmissionId(submissionId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getSubmissionId()).isEqualTo(submissionId);
    }

    @Test
    @DisplayName("존재하지 않는 submissionId로 조회하면 빈 결과를 반환한다")
    void findBySubmissionId_returnsEmpty_whenNotExists() {
        // when
        Optional<CoachingSession> found = coachingSessionRepository.findBySubmissionId(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("동일한 submissionId로 두 번 저장하면 UNIQUE 제약 위반으로 실패한다")
    void save_throwsWhenSubmissionIdAlreadyExists() {
        /*
         * 예외 변환(DataIntegrityViolationException)은 Spring이 관리하는 프록시 빈을 거쳐야
         * 적용된다. coachingSessionRepository는 이 테스트에서 new로 직접 만든 순수 객체라
         * 프록시를 안 거치므로, 대신 @Autowired로 주입받은 springDataCoachingSessionRepository
         * (실제 Spring Data JPA 빈)의 saveAndFlush로 검증한다.
         */
        // given
        UUID submissionId = UUID.randomUUID();
        springDataCoachingSessionRepository.saveAndFlush(
                CoachingSession.create(submissionId, UUID.randomUUID(), UUID.randomUUID())
        );

        CoachingSession duplicate = CoachingSession.create(submissionId, UUID.randomUUID(), UUID.randomUUID());

        // when & then
        assertThatThrownBy(() -> springDataCoachingSessionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
