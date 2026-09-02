package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.util.Validate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 오답 단계별 힌트(1~4단계) — 매삼코 DB 테이블 명세 2절.
 *
 * BaseEntity 미적용(불변 보존형, 팀 컨벤션 16절) — 상위 엔티티 CoachingSession의 불변성을
 * 물려받아 생성 후 수정·삭제되지 않는다. coachingSessionId는 같은 서비스 내부 물리 FK지만,
 * 조회 위주로만 쓰여서 @ManyToOne 없이 raw UUID 컬럼으로 유지한다(이슈 #16 결정 — 지연 로딩·N+1
 * 관리 부담 없이 필요할 때만 CoachingSessionRepository로 명시적으로 조회).
 *
 * raw UUID 컬럼이라 JPA로는 FK 제약이 DDL에 안 생기지만(@ManyToOne/@JoinColumn이 있어야 Hibernate가
 * FK를 만든다), Flyway V1 베이스라인(PR #29)이 coaching_session_id → p_coaching_sessions.id FK와
 * UNIQUE(coaching_session_id, stage)를 실제 마이그레이션 스크립트로 갖고 있어 운영 스키마에도
 * 반영돼 있다(이슈 #10 해결).
 *
 * TODO(#66): 이 테이블이 절대 삭제되지 않는 append-only라는 점을 이용해, 나중에 pgvector로
 * 임베딩해서 RAG에 활용할 수 있다(비슷한 태그·오류 패턴의 과거 힌트를 새 힌트 생성
 * 프롬프트에 few-shot으로 포함하는 식). MVP 이후 검토 — 지금 스키마는 안 건드려도 된다.
 *
 * TODO(#10): stage에 대한 CHECK (stage BETWEEN 1 AND 4) 제약은 아직 없다 — 생성자
 *            검증(requireValidStage)이 애플리케이션 레벨에서만 막고 있고, 매삼코_ERD.sql
 *            원본에도 이 CHECK가 원래 없었다(V1 베이스라인은 ERD를 그대로 옮긴 것). DB
 *            레벨 방어가 필요하면 V2 마이그레이션으로 추가할 것 — 팀에 별도 공유 필요.
 */
@Entity
@Table(
        name = "p_hints",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hints_session_stage",
                columnNames = {"coaching_session_id", "stage"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Hint {

    private static final int MIN_STAGE = 1;
    private static final int MAX_STAGE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "coaching_session_id", updatable = false, nullable = false)
    private UUID coachingSessionId;

    @Column(name = "stage", updatable = false, nullable = false)
    private int stage;

    @Column(name = "content", updatable = false, nullable = false, columnDefinition = "TEXT")
    private String content;

    /*
     * TODO: created_at이 실제로 TIMESTAMPTZ 컬럼으로 생성되는지 검증하는 회귀 테스트가
     * 없다(BaseEntity처럼 information_schema.columns.data_type을 직접 확인하는 테스트,
     * PR #11에서 BaseEntity 쪽에 이미 지적된 것과 같은 성격 — 이 엔티티는 BaseEntity를
     * 상속하지 않아 별도로 필요). 누군가 실수로 Instant를 LocalDateTime으로 되돌려도
     * 지금은 CI가 못 잡아낸다. Repository 통합 테스트에 추가할 것.
     */
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Builder
    private Hint(UUID coachingSessionId, int stage, String content) {
        this.coachingSessionId = Validate.requireNonNull(coachingSessionId, "코칭 세션 ID");
        this.stage = requireValidStage(stage);
        this.content = Validate.requireText(content, "힌트 본문");
    }

    public static Hint create(UUID coachingSessionId, int stage, String content) {
        return Hint.builder()
                .coachingSessionId(coachingSessionId)
                .stage(stage)
                .content(content)
                .build();
    }

    private static int requireValidStage(int stage) {
        if (stage < MIN_STAGE || stage > MAX_STAGE) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "힌트 단계는 " + MIN_STAGE + "~" + MAX_STAGE + " 사이여야 합니다."
            );
        }
        return stage;
    }
}
