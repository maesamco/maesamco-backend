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
     * last_detected_at이 실제로 TIMESTAMPTZ 컬럼으로 생성되는지는
     * TimestamptzColumnRegressionTest(이슈 #218)가 검증한다.
     */
    @Column(name = "last_detected_at", nullable = false)
    private Instant lastDetectedAt;

    @Column(name = "improved", nullable = false)
    private boolean improved;

    @Builder
    private WeakConcept(UUID userId, String conceptTag) {
        this.userId = Validate.requireNonNull(userId, "사용자 ID");
        // 앞뒤 공백만 다른 태그("재귀" vs " 재귀 ")가 UNIQUE 제약상 서로 다른 개념으로
        // 저장되지 않도록 trim 후 검증한다(PR #34 리뷰) — trim은 이제 Validate.requireText()가 기본으로 한다(이슈 #45).
        this.conceptTag = Validate.requireText(conceptTag, 50, "개념 태그");
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

    public void markImproved() {
        this.improved = true;
    }

}
