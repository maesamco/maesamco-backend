package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Test
    @DisplayName("이슈 #54 — 개선 안 된 것 우선, 그다음 발견 횟수 높은 순으로 정렬해 조회한다")
    void findByUserIdOrderByImprovedAscOccurrenceCountDescLastDetectedAtDesc_ordersByPriority() {
        // given — 코칭 서비스 API 명세 7번 API 우선순위: improved=false 우선, 그다음 occurrenceCount desc
        UUID userId = UUID.randomUUID();

        WeakConcept improved = weakConceptRepository.save(WeakConcept.create(userId, "예외 처리"));
        improved.markImproved();
        weakConceptRepository.save(improved);

        WeakConcept lowCount = weakConceptRepository.save(WeakConcept.create(userId, "재귀"));

        // 원자적 upsert(recordOccurrence())를 두 번 호출한다 — 첫 호출이 최초 생성
        // (occurrenceCount=1), 두 번째 호출이 재발견 갱신(occurrenceCount=2)이다.
        weakConceptRepository.recordOccurrence(userId, "경계값 처리", Instant.now());
        weakConceptRepository.recordOccurrence(userId, "경계값 처리", Instant.now());

        entityManager.flush();
        entityManager.clear();

        // when
        List<WeakConcept> found = weakConceptRepository.findByUserIdOrderByImprovedAscOccurrenceCountDescLastDetectedAtDesc(userId);

        // then
        assertThat(found).extracting(WeakConcept::getConceptTag)
                .containsExactly("경계값 처리", "재귀", "예외 처리");
    }

    @Test
    @DisplayName("취약 개념이 없는 사용자를 조회하면 빈 목록을 반환한다")
    void findByUserIdOrderByImprovedAscOccurrenceCountDescLastDetectedAtDesc_returnsEmpty_whenNoWeakConcepts() {
        // when
        List<WeakConcept> found =
                weakConceptRepository.findByUserIdOrderByImprovedAscOccurrenceCountDescLastDetectedAtDesc(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("신규 (userId, conceptTag)를 원자적으로 생성한다")
    void recordOccurrence_createsNewRow() {
        // given
        UUID userId = UUID.randomUUID();
        Instant detectedAt = Instant.parse("2026-01-01T00:00:00Z");

        // when
        weakConceptRepository.recordOccurrence(userId, "재귀", detectedAt);

        // then
        WeakConcept found = weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀").orElseThrow();
        assertThat(found.getOccurrenceCount()).isEqualTo(1);
        assertThat(found.getLastDetectedAt()).isEqualTo(detectedAt);
        assertThat(found.isImproved()).isFalse();
    }

    @Test
    @DisplayName("이미 있는 (userId, conceptTag)는 발견 횟수·시각만 원자적으로 갱신한다")
    void recordOccurrence_updatesExistingRow() {
        // given
        UUID userId = UUID.randomUUID();
        weakConceptRepository.recordOccurrence(userId, "재귀", Instant.parse("2026-01-01T00:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        Instant secondDetectedAt = Instant.parse("2026-01-02T00:00:00Z");

        // when
        weakConceptRepository.recordOccurrence(userId, "재귀", secondDetectedAt);

        // then
        WeakConcept found = weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀").orElseThrow();
        assertThat(found.getOccurrenceCount()).isEqualTo(2);
        assertThat(found.getLastDetectedAt()).isEqualTo(secondDetectedAt);
    }

    /**
     * PR #166 리뷰(용현님 P1) 대응 회귀 테스트 — 기존 find-then-branch-then-save 방식은
     * 두 트랜잭션이 동시에 같은 (userId, conceptTag)를 최초 발견하면, 나중에 flush되는
     * 쪽이 UNIQUE 위반으로 트랜잭션이 abort되고 그 안에서의 재조회 복구도 실패했다(같은
     * 트랜잭션에서는 abort 이후 어떤 명령도 성공할 수 없음, PostgreSQL). 실제로 서로 다른
     * 스레드(=서로 다른 커넥션·트랜잭션)에서 정확히 같은 (userId, conceptTag)를 동시에
     * INSERT 시도해도, 원자적 upsert라 예외 없이 둘 다 성공하고 발견 횟수가 정확히 2로
     * 반영되는지 검증한다.
     */
    @Test
    @DisplayName("동시에 두 트랜잭션이 같은 (userId, conceptTag)를 최초 발견해도 예외 없이 둘 다 반영된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void recordOccurrence_isAtomicUnderConcurrentFirstDiscovery() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String conceptTag = "동시성 테스트";
        Instant detectedAt = Instant.now();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Void> task = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            weakConceptRepository.recordOccurrence(userId, conceptTag, detectedAt);
            return null;
        };

        // when — 두 스레드(=서로 다른 커넥션·트랜잭션)가 거의 동시에 같은 행을 최초 생성 시도
        List<Future<Void>> futures = executor.invokeAll(List.of(task, task));
        executor.shutdown();

        // then — 어느 쪽도 예외 없이 끝나야 한다(둘 중 하나는 INSERT, 하나는 ON CONFLICT UPDATE)
        for (Future<Void> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }

        WeakConcept result = weakConceptRepository.findByUserIdAndConceptTag(userId, conceptTag).orElseThrow();
        assertThat(result.getOccurrenceCount()).isEqualTo(2);
    }
}
