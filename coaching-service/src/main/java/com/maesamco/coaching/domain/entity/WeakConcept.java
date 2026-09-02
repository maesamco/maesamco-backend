package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.util.Validate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 사용자별 취약 개념 집계 — 매삼코 DB 테이블 명세 7절.
 *
 * BaseEntity 미적용 — 사용자당 개념별 1행이 계속 갱신되는 집계 테이블이라 "언제 생성됐는지"는
 * 의미가 없다. 명세 원문은 "사용자 삭제 시 함께 정리된다"고 되어 있지만, userId가 User
 * Service에 대한 논리 FK라 실제로는 그 정리를 수행할 경로(이벤트 소비, 내부 API 등)가 아직
 * 전혀 없다 — User Service가 삭제 사실을 다른 서비스에 알릴 방법 자체가 없다(이슈 #39, 팀
 * 논의 필요). 그 전까지는 "독립적인 소프트 삭제는 필요 없다"는 명세 판단만 유지한다.
 *
 * 지금까지의 Coaching 엔티티(CoachingSession 제외)와 달리 불변/append-only가 아니라, 같은
 * (user_id, concept_tag)가 재발견될 때마다 새 행을 만들지 않고 기존 행을 갱신하는 집계
 * 엔티티다(DB 테이블 명세 06절: "발견 시 count만 증가"). userId는 User Service에 대한 논리
 * FK(서비스 간 참조)라 같은 서비스 내부 FK와 달리 물리 FK 자체가 성립하지 않는다.
 *
 * UNIQUE(user_id, concept_tag) 제약은 Flyway V1 베이스라인(PR #29)이 실제 마이그레이션
 * 스크립트로 갖고 있어 운영 스키마에도 반영돼 있다(이슈 #10 해결) — 운영 스키마의 유일한
 * 소스는 마이그레이션 스크립트다. @Table의 uniqueConstraints는 스키마를 생성하는 역할이
 * 아니라(WeakConceptRepositoryImplTest도 실제 Flyway V1 스키마로 검증한다), 엔티티 코드만
 * 보고도 제약을 바로 알 수 있게 문서 목적으로 중복 명시해둔 것이다.
 */
@Entity
@Table(
        name = "p_weak_concepts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weak_concepts_user_concept",
                columnNames = {"user_id", "concept_tag"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeakConcept {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "concept_tag", updatable = false, nullable = false, length = 50)
    private String conceptTag;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    /*
     * TODO: last_detected_at이 실제로 TIMESTAMPTZ 컬럼으로 생성되는지 검증하는 회귀 테스트가
     * 없다(AiFeedback의 information_schema.columns.data_type 검증 테스트와 같은 성격, PR #33).
     * 누군가 실수로 Instant를 LocalDateTime으로 되돌려도 지금은 CI가 못 잡아낸다.
     * Repository 통합 테스트에 추가할 것.
     */
    @Column(name = "last_detected_at", nullable = false)
    private Instant lastDetectedAt;

    @Column(name = "improved", nullable = false)
    private boolean improved;

    @Builder
    private WeakConcept(UUID userId, String conceptTag) {
        this.userId = Validate.requireNonNull(userId, "사용자 ID");
        // 앞뒤 공백만 다른 태그("재귀" vs " 재귀 ")가 UNIQUE 제약상 서로 다른 개념으로
        // 저장되지 않도록 trim 후 검증한다(PR #34 리뷰).
        this.conceptTag = Validate.requireText(conceptTag == null ? null : conceptTag.trim(), 50, "개념 태그");
        this.occurrenceCount = 1;
        this.lastDetectedAt = Instant.now();
        this.improved = false;
    }

    public static WeakConcept create(UUID userId, String conceptTag) {
        return WeakConcept.builder()
                .userId(userId)
                .conceptTag(conceptTag)
                .build();
    }

    /**
     * 같은 개념이 다시 발견됐을 때 호출 — 새 행을 만들지 않고 기존 집계 행의 발견 횟수/시각만
     * 갱신한다.
     *
     * ⚠️ CoachingSession.complete()와 동일하게 낙관적 락 없이 값을 읽고 바로 갱신한다(check-
     * then-act). (user_id, concept_tag) 조회 후 갱신하는 Service/Facade를 만들 때, 같은
     * 개념이 짧은 시간에 동시에 재발견되는 경로의 동시성 처리 여부를 함께 고려할 것.
     *
     * TODO: 취약 개념 조회 후 갱신(find-then-update) Service/Facade 구현 시 위 동시성 문제
     *       해결 방안 확정하고 이 TODO 제거.
     *
     * TODO: improved=true로 표시된 행이 나중에 다시 발견되면(occurrenceCount 증가) improved를
     *       false로 되돌려야 하는지가 명세에 없다. 지금은 이 메서드가 improved를 건드리지 않고
     *       그대로 둔다 — 재발견 시 되돌릴지 여부는 응용 계층에서 결정하고 이 TODO 제거.
     */
    public void recordOccurrence() {
        recordOccurrence(Instant.now());
    }

    /**
     * recordOccurrence()가 내부적으로 호출하는 오버로드 — 발견 시각을 직접 주입할 수 있게
     * 분리했다(PR #34 리뷰). Instant.now()만 쓰면 두 번의 호출이 같은 나노초에 걸릴 가능성이
     * 0은 아니라서 "정확히 이 시각으로 갱신됐는지" 자체를 테스트로 결정적으로 검증하기 어려운데,
     * 이 오버로드로 고정된 Instant를 넘겨서 검증할 수 있다. 나중에 이벤트의 발생 시각을 그대로
     * 반영해야 하는 경우에도 그대로 쓸 수 있다.
     */
    public void recordOccurrence(Instant detectedAt) {
        // occurrenceCount는 DB 컬럼도 INT(32비트)라 Integer.MAX_VALUE에서 그냥 += 1하면
        // 조용히 음수로 뒤집힌다 — 실제로 21억 번 발견될 일은 거의 없지만(PR #34 리뷰),
        // addExact로 넘칠 때 조용히 틀린 값이 되는 대신 명시적으로 실패하게 한다.
        this.occurrenceCount = Math.addExact(this.occurrenceCount, 1);
        this.lastDetectedAt = Validate.requireNonNull(detectedAt, "발견 시각");
    }

    public void markImproved() {
        this.improved = true;
    }

}
