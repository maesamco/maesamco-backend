package com.maesamco.coaching.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 종합 이해도 피드백 — 매삼코 DB 테이블 명세 6절.
 *
 * BaseEntity 미적용(불변 보존형, 팀 컨벤션 16절) — 상위 엔티티 CoachingSession의 불변성을
 * 물려받아 생성 후 수정·삭제되지 않는다. coachingSessionId는 같은 서비스 내부 물리 FK지만,
 * Hint/Explanation 등과 동일한 이유로 @ManyToOne 없이 raw UUID 컬럼으로 유지한다(지연 로딩·N+1
 * 관리 부담 없이 필요할 때만 CoachingSessionRepository로 명시적으로 조회).
 *
 * JSONB 컬럼은 이 프로젝트에서 이 엔티티가 처음이다. 각 필드의 내부 구조(개념 목록인지, 문제
 * ID 목록인지 등)가 아직 확정되지 않았고 AI 응답을 실제로 파싱하는 로직도 이후 PR의 몫이라,
 * 특정 Java 타입(Map/List/DTO)으로 미리 단정하지 않고 Jackson의 {@link JsonNode}로 임의의
 * JSON 트리를 그대로 담는다 — Hibernate 6+가 클래스패스의 Jackson ObjectMapper를 자동으로
 * 사용해 {@code @JdbcTypeCode(SqlTypes.JSON)} + JsonNode 조합을 직렬화/역직렬화한다(실제
 * Testcontainers PostgreSQL로 왕복 검증 완료).
 *
 * ⚠️ raw UUID 컬럼이라 JPA로는 이 FK 제약이 DDL에 생성되지 않는다(@ManyToOne/@JoinColumn이
 * 있어야 Hibernate가 FK를 만든다). UNIQUE(coaching_session_id) 제약도 지금 @Table로 JPA/테스트
 * 레벨에만 있고, 운영 환경은 ddl-auto=validate라 Flyway 마이그레이션 스크립트가 유일한 스키마
 * 소스가 된다.
 *
 * TODO(#10): Flyway 마이그레이션 도입 시 p_ai_feedbacks에 아래 두 제약 추가.
 *            1) coaching_session_id에 REFERENCES p_coaching_sessions(id) FK 제약.
 *            2) UNIQUE(coaching_session_id) — 지금 JPA 애노테이션에만 있는 제약을
 *               마이그레이션 스크립트에도 명시적으로 포함.
 */
@Entity
@Table(
        name = "p_ai_feedbacks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_feedbacks_session",
                columnNames = {"coaching_session_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AiFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "coaching_session_id", updatable = false, nullable = false)
    private UUID coachingSessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "understood_concepts", updatable = false, nullable = false)
    private JsonNode understoodConcepts;

    /**
     * 설명이 부족한 부분 — 구 weak_points에서 개명(weak_concepts 컬럼·p_weak_concepts
     * 테이블과 혼동 방지 목적).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation_gaps", updatable = false, nullable = false)
    private JsonNode explanationGaps;

    /**
     * 취약 개념 요약값 — 상세 집계는 p_weak_concepts.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weak_concepts", updatable = false, nullable = false)
    private JsonNode weakConcepts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "syntax_to_improve", updatable = false)
    private JsonNode syntaxToImprove;

    /**
     * 추천 복습 문제 ID 목록.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_problems", updatable = false)
    private JsonNode recommendedProblems;

    @Column(name = "next_direction", updatable = false, columnDefinition = "TEXT")
    private String nextDirection;

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
    private AiFeedback(
            UUID coachingSessionId,
            JsonNode understoodConcepts,
            JsonNode explanationGaps,
            JsonNode weakConcepts,
            JsonNode syntaxToImprove,
            JsonNode recommendedProblems,
            String nextDirection
    ) {
        this.coachingSessionId = requireNonNull(coachingSessionId, "코칭 세션 ID");
        this.understoodConcepts = requireNonNull(understoodConcepts, "이해한 개념");
        this.explanationGaps = requireNonNull(explanationGaps, "설명 부족 부분");
        this.weakConcepts = requireNonNull(weakConcepts, "취약 개념");
        this.syntaxToImprove = syntaxToImprove;
        this.recommendedProblems = recommendedProblems;
        this.nextDirection = nextDirection;
    }

    public static AiFeedback create(
            UUID coachingSessionId,
            JsonNode understoodConcepts,
            JsonNode explanationGaps,
            JsonNode weakConcepts,
            JsonNode syntaxToImprove,
            JsonNode recommendedProblems,
            String nextDirection
    ) {
        return AiFeedback.builder()
                .coachingSessionId(coachingSessionId)
                .understoodConcepts(understoodConcepts)
                .explanationGaps(explanationGaps)
                .weakConcepts(weakConcepts)
                .syntaxToImprove(syntaxToImprove)
                .recommendedProblems(recommendedProblems)
                .nextDirection(nextDirection)
                .build();
    }

    // TODO: CoachingSession.java/Hint.java 등에도 거의 동일한 requireNonNull이 따로
    //       구현돼 있다(User Service의 User/UserInterestConcept와 같은 중복 패턴).
    //       지금은 엔티티가 6개뿐이라 문제 없지만, coaching-service에 엔티티가 더
    //       추가되면 global/util 공통 Validate 유틸로 추출을 고려할 것.
    private static UUID requireNonNull(UUID value, String fieldNameKorean) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + "는 필수입니다.");
        }
        return value;
    }

    private static JsonNode requireNonNull(JsonNode value, String fieldNameKorean) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + "은 필수입니다.");
        }
        return value;
    }
}
