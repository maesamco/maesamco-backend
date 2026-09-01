package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
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
 * 사용자별 취약 개념 집계 — 매삼코 DB 테이블 명세 05-7절.
 *
 * BaseEntity 미적용 — 사용자당 개념별 1행이 계속 갱신되는 집계 테이블이라 "언제 생성됐는지"는
 * 의미가 없고, 사용자 삭제 시 함께 정리되므로 독립적인 소프트 삭제도 불필요하다(명세 원문).
 *
 * 지금까지의 Coaching 엔티티(CoachingSession 제외)와 달리 불변/append-only가 아니라, 같은
 * (user_id, concept_tag)가 재발견될 때마다 새 행을 만들지 않고 기존 행을 갱신하는 집계
 * 엔티티다(DB 테이블 명세 06절: "발견 시 count만 증가"). userId는 User Service에 대한 논리
 * FK(서비스 간 참조)라 같은 서비스 내부 FK와 달리 물리 FK 자체가 성립하지 않는다.
 *
 * ⚠️ UNIQUE(user_id, concept_tag) 제약이 지금 @Table로 JPA/테스트 레벨에만 있고, 운영 환경은
 * ddl-auto=validate라 Flyway 마이그레이션 스크립트가 유일한 스키마 소스가 된다.
 *
 * TODO(#10): Flyway 마이그레이션 도입 시 p_weak_concepts에 UNIQUE(user_id, concept_tag)
 *            제약을 마이그레이션 스크립트에도 명시적으로 포함.
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
        this.userId = requireNonNull(userId, "사용자 ID");
        this.conceptTag = requireText(conceptTag, "개념 태그");
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
        this.occurrenceCount += 1;
        this.lastDetectedAt = Instant.now();
    }

    public void markImproved() {
        this.improved = true;
    }

    // TODO: CoachingSession.java/Hint.java/Explanation.java 등에도 거의 동일한 requireNonNull이
    //       따로 구현돼 있다(User Service의 User/UserInterestConcept와 같은 중복 패턴). 지금은
    //       엔티티가 늘어나는 중이니, coaching-service 도메인 구현이 마무리되면 global/util
    //       공통 Validate 유틸로 추출을 고려할 것.
    private static UUID requireNonNull(UUID value, String fieldNameKorean) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + "는 필수입니다.");
        }
        return value;
    }

    private static String requireText(String value, String fieldNameKorean) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + "은 필수입니다.");
        }
        return value;
    }
}
