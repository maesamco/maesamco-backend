package com.maesamco.coaching.domain.entity;

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
 * 정답 제출에 대한 60초 설명 — 매삼코 DB 테이블 명세 3절.
 *
 * BaseEntity 미적용(불변 보존형, 팀 컨벤션 16절) — 상위 엔티티 CoachingSession의 불변성을
 * 물려받아 생성 후 수정·삭제되지 않는다. coachingSessionId는 같은 서비스 내부 물리 FK지만,
 * Hint(이슈 #16)와 동일한 이유로 @ManyToOne 없이 raw UUID 컬럼으로 유지한다(지연 로딩·N+1
 * 관리 부담 없이 필요할 때만 CoachingSessionRepository로 명시적으로 조회).
 *
 * MVP는 텍스트만 지원(음성·STT는 MVP 이후).
 *
 * 🔧 2026-09-03(이슈 #84 결정 2): 유일성 기준을 coaching_session_id에서 submission_id로
 * 옮겼다. 힌트/스트릭은 "막혔을 때 도와주는" 장치라 문제당 평생 1세션(결정 1)이 맞지만,
 * 60초 설명은 "이번 제출의 접근 방식을 이해했는가"를 확인하는 장치라 같은 문제를 다른
 * 접근으로 재도전해 새로 정답 제출할 때마다 별도로 등록할 수 있어야 한다. coachingSessionId는
 * 이 설명이 어느 세션(문제) 소속인지 추적하는 용도로 컬럼 자체는 남기지만(내부 API #8 등에서
 * 필요), 더 이상 유일성 기준이 아니다 — 같은 세션에 여러 설명이 존재할 수 있다.
 *
 * raw UUID 컬럼이라 JPA로는 FK 제약이 DDL에 안 생기지만(@ManyToOne/@JoinColumn이 있어야
 * Hibernate가 FK를 만든다), Flyway V6(이슈 #84 결정 2)가 coaching_session_id →
 * p_coaching_sessions.id FK와 UNIQUE(submission_id)를 실제 마이그레이션 스크립트로 갖고
 * 있어 운영 스키마에도 반영돼 있다(이슈 #10 해결). submission_id는 다른 서비스(Judge)의
 * 리소스라 물리 FK 없이 논리 FK로만 참조한다(팀 컨벤션 16절, 서비스 경계 원칙).
 */
@Entity
@Table(
        name = "p_explanations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_explanations_submission",
                columnNames = {"submission_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Explanation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "coaching_session_id", updatable = false, nullable = false)
    private UUID coachingSessionId;

    @Column(name = "submission_id", updatable = false, nullable = false)
    private UUID submissionId;

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
    private Explanation(UUID coachingSessionId, UUID submissionId, String content) {
        this.coachingSessionId = Validate.requireNonNull(coachingSessionId, "코칭 세션 ID");
        this.submissionId = Validate.requireNonNull(submissionId, "제출 ID");
        this.content = Validate.requireText(content, "설명 본문");
    }

    public static Explanation create(UUID coachingSessionId, UUID submissionId, String content) {
        return Explanation.builder()
                .coachingSessionId(coachingSessionId)
                .submissionId(submissionId)
                .content(content)
                .build();
    }
}
