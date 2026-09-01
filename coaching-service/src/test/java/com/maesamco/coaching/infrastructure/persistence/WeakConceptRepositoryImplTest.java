package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.WeakConcept;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
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
 *
 * WeakConcept은 @CreatedDate/@LastModifiedDate 같은 JPA 감사(Auditing)를 쓰지 않으므로
 * (lastDetectedAt은 도메인 메서드로 직접 관리) @EnableJpaAuditing이 필요 없다. 다른 Coaching
 * 엔티티와 달리 물리 FK도 없어서(userId는 User Service에 대한 논리 FK) 부모 행을 미리
 * 저장해두는 준비 작업도 필요 없다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class WeakConceptRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private SpringDataWeakConceptRepository springDataWeakConceptRepository;

    @Autowired
    private EntityManager entityManager;

    private WeakConceptRepositoryImpl weakConceptRepository;

    @BeforeEach
    void setUp() {
        weakConceptRepository = new WeakConceptRepositoryImpl(springDataWeakConceptRepository);
    }

    @Test
    @DisplayName("취약 개념을 저장하면 ID가 채번되고 발견 횟수 1·improved false로 초기화된다")
    void save_assignsIdAndDefaults() {
        // given
        WeakConcept weakConcept = WeakConcept.create(UUID.randomUUID(), "재귀");

        // when
        WeakConcept saved = weakConceptRepository.save(weakConcept);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOccurrenceCount()).isEqualTo(1);
        assertThat(saved.isImproved()).isFalse();
        assertThat(saved.getLastDetectedAt()).isNotNull();
    }

    @Test
    @DisplayName("(userId, conceptTag)로 취약 개념을 조회할 수 있다")
    void findByUserIdAndConceptTag_returnsWeakConcept() {
        // given
        UUID userId = UUID.randomUUID();
        weakConceptRepository.save(WeakConcept.create(userId, "재귀"));

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<WeakConcept> found = weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().getConceptTag()).isEqualTo("재귀");
        assertThat(found.get().getOccurrenceCount()).isEqualTo(1);
        assertThat(found.get().isImproved()).isFalse();
        assertThat(found.get().getLastDetectedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 (userId, conceptTag)로 조회하면 빈 결과를 반환한다")
    void findByUserIdAndConceptTag_returnsEmpty_whenNotExists() {
        // when
        Optional<WeakConcept> found =
                weakConceptRepository.findByUserIdAndConceptTag(UUID.randomUUID(), "재귀");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("recordOccurrence(Instant) 후 다시 저장하면 발견 횟수·시각이 실제 DB에도 정확히 반영된다")
    void recordOccurrence_persistsUpdatedCountAndExactTimestamp() {
        // given — isAfterOrEqualTo만으로는 lastDetectedAt 갱신이 실수로 빠져도 못 잡아낸다
        // (PR #34 리뷰). 시각을 직접 주입해서 정확한 값이 DB에도 그대로 반영되는지 확인한다.
        UUID userId = UUID.randomUUID();
        WeakConcept saved = weakConceptRepository.save(WeakConcept.create(userId, "재귀"));
        entityManager.flush();
        entityManager.clear();

        WeakConcept found = weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀").orElseThrow();
        Instant detectedAt = Instant.parse("2026-01-01T00:00:00Z");

        // when
        found.recordOccurrence(detectedAt);
        weakConceptRepository.save(found);
        entityManager.flush();
        entityManager.clear();

        // then
        WeakConcept reloaded = weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀").orElseThrow();
        assertThat(reloaded.getOccurrenceCount()).isEqualTo(2);
        assertThat(reloaded.getLastDetectedAt()).isEqualTo(detectedAt);
        assertThat(reloaded.getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("markImproved() 후 다시 저장하면 improved=true가 실제 DB에도 반영된다")
    void markImproved_persistsImprovedFlag() {
        // given
        UUID userId = UUID.randomUUID();
        weakConceptRepository.save(WeakConcept.create(userId, "재귀"));
        entityManager.flush();
        entityManager.clear();

        WeakConcept found = weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀").orElseThrow();

        // when
        found.markImproved();
        weakConceptRepository.save(found);
        entityManager.flush();
        entityManager.clear();

        // then
        WeakConcept reloaded = weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀").orElseThrow();
        assertThat(reloaded.isImproved()).isTrue();
    }

    @Test
    @DisplayName("동일한 (userId, conceptTag)로 두 번 저장하면 WEAK_CONCEPT_ALREADY_EXISTS(409)로 실패한다")
    void save_throwsWhenUserIdAndConceptTagAlreadyExists() {
        // given
        UUID userId = UUID.randomUUID();
        weakConceptRepository.save(WeakConcept.create(userId, "재귀"));

        WeakConcept duplicate = WeakConcept.create(userId, "재귀");

        // when & then
        assertThatThrownBy(() -> weakConceptRepository.save(duplicate))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WEAK_CONCEPT_ALREADY_EXISTS)
                );
    }
}
