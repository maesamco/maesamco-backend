package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WeakConcept은 @CreatedDate/@LastModifiedDate 같은 JPA 감사(Auditing)를 쓰지 않지만
 * (lastDetectedAt은 도메인 메서드로 직접 관리) 부모 클래스의 @EnableJpaAuditing이 그냥
 * 켜져 있어도 이 엔티티엔 영향이 없다. 다른 Coaching 엔티티와 달리 물리 FK도 없어서
 * (userId는 User Service에 대한 논리 FK) 부모 행을 미리 저장해두는 준비 작업도 필요 없다.
 */
class WeakConceptRepositoryImplTest extends AbstractCoachingRepositoryTest {

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
    @DisplayName("앞뒤 공백이 붙은 conceptTag로 조회해도 trim된 저장 값을 찾는다")
    void findByUserIdAndConceptTag_trimsQueryConceptTag() {
        // given
        UUID userId = UUID.randomUUID();
        weakConceptRepository.save(WeakConcept.create(userId, "재귀"));

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<WeakConcept> found = weakConceptRepository.findByUserIdAndConceptTag(userId, "  재귀  ");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getConceptTag()).isEqualTo("재귀");
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
