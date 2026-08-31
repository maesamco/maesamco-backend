package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.Hint;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 팀 컨벤션 18절 — Repository 통합 테스트는 H2가 아니라 Testcontainers 실제 PostgreSQL로 검증한다.
 *
 * ⚠️ Flyway/Liquibase 등 마이그레이션 도구가 아직 팀 차원에서 결정되지 않아(이슈 #10),
 *    운영 마이그레이션 스크립트 대신 테스트 전용 ddl-auto=create-drop으로 coaching_schema를
 *    직접 생성해서 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableJpaAuditing
@Testcontainers
class HintRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private SpringDataHintRepository springDataHintRepository;

    @Autowired
    private EntityManager entityManager;

    private HintRepositoryImpl hintRepository;

    @BeforeEach
    void setUp() {
        hintRepository = new HintRepositoryImpl(springDataHintRepository);
    }

    @Test
    @DisplayName("힌트를 저장하면 ID와 생성 시각이 채워진다")
    void save_assignsIdAndCreatedAt() {
        // given
        Hint hint = Hint.create(UUID.randomUUID(), 1, "내용");

        // when
        Hint saved = hintRepository.save(hint);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("코칭 세션 ID로 힌트 전체를 단계 오름차순으로 조회할 수 있다")
    void findByCoachingSessionId_returnsAllHintsOrderedByStage() {
        // given — 단계 역순으로 저장해도 조회 결과는 오름차순이어야 한다
        UUID coachingSessionId = UUID.randomUUID();
        hintRepository.save(Hint.create(coachingSessionId, 2, "2단계 힌트"));
        hintRepository.save(Hint.create(coachingSessionId, 1, "1단계 힌트"));
        hintRepository.save(Hint.create(UUID.randomUUID(), 1, "다른 세션 힌트"));

        entityManager.flush();
        entityManager.clear();

        // when
        List<Hint> found = hintRepository.findByCoachingSessionId(coachingSessionId);

        // then
        assertThat(found)
                .extracting(Hint::getStage)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("코칭 세션 ID와 단계로 힌트 하나를 조회할 수 있다")
    void findByCoachingSessionIdAndStage_returnsHint() {
        // given
        UUID coachingSessionId = UUID.randomUUID();
        hintRepository.save(Hint.create(coachingSessionId, 3, "3단계 힌트"));

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<Hint> found = hintRepository.findByCoachingSessionIdAndStage(coachingSessionId, 3);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("3단계 힌트");
    }

    @Test
    @DisplayName("존재하지 않는 세션·단계 조합으로 조회하면 빈 결과를 반환한다")
    void findByCoachingSessionIdAndStage_returnsEmpty_whenNotExists() {
        // when
        Optional<Hint> found = hintRepository.findByCoachingSessionIdAndStage(UUID.randomUUID(), 1);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은 세션에 같은 단계의 힌트를 두 번 저장하면 UNIQUE 제약 위반으로 실패한다")
    void save_throwsWhenSessionAndStageAlreadyExists() {
        /*
         * 예외 변환(DataIntegrityViolationException)은 Spring이 관리하는 프록시 빈을 거쳐야
         * 적용되므로, new로 직접 만든 hintRepository 대신 @Autowired로 주입받은
         * springDataHintRepository(Spring Data JPA 빈)의 saveAndFlush로 검증한다.
         */
        // given
        UUID coachingSessionId = UUID.randomUUID();
        springDataHintRepository.saveAndFlush(Hint.create(coachingSessionId, 1, "1단계 힌트"));

        Hint duplicate = Hint.create(coachingSessionId, 1, "같은 단계 재생성");

        // when & then
        assertThatThrownBy(() -> springDataHintRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
