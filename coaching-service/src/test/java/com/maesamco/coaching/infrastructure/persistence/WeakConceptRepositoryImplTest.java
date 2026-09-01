package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.WeakConcept;
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
import org.springframework.test.context.TestPropertySource;
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
 * WeakConcept은 @CreatedDate/@LastModifiedDate 같은 JPA 감사(Auditing)를 쓰지 않으므로
 * (lastDetectedAt은 도메인 메서드로 직접 관리) @EnableJpaAuditing이 필요 없다.
 *
 * ⚠️ create_namespaces=true가 필요하다 — Hibernate 7의 hbm2ddl.create_namespaces 기본값은
 * false라, default_schema로만 지정된 coaching_schema는 create-drop이어도 자동으로 만들어주지
 * 않는다(CREATE SCHEMA 없이 CREATE TABLE coaching_schema.xxx만 시도하다가
 * "schema coaching_schema does not exist"로 실패). 다른 Coaching Repository 테스트들이 이
 * 옵션 없이도 통과하는 건 같은 EntityManagerFactory 안에 이미 다른 엔티티(CoachingSession 등)가
 * 함께 등록돼 있어서인 것으로 보인다 — WeakConcept은 develop에서 바로 분기해 이 서비스의
 * 유일한 엔티티라 그 우연한 경로를 못 타서 명시적으로 켜야 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true")
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
    @DisplayName("recordOccurrence() 후 다시 저장하면 발견 횟수·시각이 실제 DB에도 갱신된다")
    void recordOccurrence_persistsUpdatedCountAndTimestamp() {
        // given
        UUID userId = UUID.randomUUID();
        WeakConcept saved = weakConceptRepository.save(WeakConcept.create(userId, "재귀"));
        entityManager.flush();
        entityManager.clear();

        WeakConcept found = weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀").orElseThrow();
        var firstDetectedAt = found.getLastDetectedAt();

        // when
        found.recordOccurrence();
        weakConceptRepository.save(found);
        entityManager.flush();
        entityManager.clear();

        // then
        WeakConcept reloaded = weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀").orElseThrow();
        assertThat(reloaded.getOccurrenceCount()).isEqualTo(2);
        assertThat(reloaded.getLastDetectedAt()).isAfterOrEqualTo(firstDetectedAt);
        assertThat(reloaded.getId()).isEqualTo(saved.getId());
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
