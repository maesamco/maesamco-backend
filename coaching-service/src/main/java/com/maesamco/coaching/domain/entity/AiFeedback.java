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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tools.jackson.databind.JsonNode;

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
 * 특정 Java 타입(Map/List/DTO)으로 미리 단정하지 않고 Jackson의 JsonNode로 임의의
 * JSON 트리를 그대로 담는다 — @JdbcTypeCode(SqlTypes.JSON) + JsonNode 조합을 Hibernate가
 * 직렬화/역직렬화한다(실제 Testcontainers PostgreSQL로 왕복 검증 완료).
 *
 * 2026-09-03, Jackson 3(tools.jackson)로 전환하며 확인된 것: 클래스패스에 Jackson 2/3이
 * 둘 다 있으면(Spring Boot 4의 spring-boot-starter-jackson이 둘 다 가져옴) Hibernate는
 * 자동 감지 시 기본적으로 구버전(Jackson 2)의 FormatMapper를 고른다 — Spring MVC의 HTTP
 * 컨버터 기본값(Jackson 3 우선)과 정반대 방향이라 헷갈리기 쉽다. Jackson 2 매퍼로는
 * tools.jackson.databind.JsonNode를 역직렬화할 수 없어(InvalidDefinitionException, 실제
 * 재현함) application.yml에 spring.jpa.properties.hibernate.type.json_format_mapper를
 * Jackson3JsonFormatMapper로 명시해야 한다(운영/테스트 yml 둘 다).
 *
 * raw UUID 컬럼이라 JPA로는 FK 제약이 DDL에 안 생기지만(@ManyToOne/@JoinColumn이 있어야
 * Hibernate가 FK를 만든다), Flyway V1 베이스라인(PR #29)이 coaching_session_id →
 * p_coaching_sessions.id FK와 UNIQUE(coaching_session_id)를 실제 마이그레이션 스크립트로
 * 갖고 있어 운영 스키마에도 반영돼 있다(이슈 #10 해결).
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
        this.coachingSessionId = Validate.requireNonNull(coachingSessionId, "코칭 세션 ID");
        this.understoodConcepts = Validate.requireNonNull(understoodConcepts, "이해한 개념").deepCopy();
        this.explanationGaps = Validate.requireNonNull(explanationGaps, "설명 부족 부분").deepCopy();
        this.weakConcepts = Validate.requireNonNull(weakConcepts, "취약 개념").deepCopy();
        this.syntaxToImprove = syntaxToImprove == null ? null : syntaxToImprove.deepCopy();
        this.recommendedProblems = recommendedProblems == null ? null : recommendedProblems.deepCopy();
        this.nextDirection = nextDirection;
    }

    /**
     * 불변 보존형 엔티티지만 JsonNode 자체는 ArrayNode/ObjectNode처럼 mutable할 수 있어,
     * Lombok @Getter가 만드는 기본 getter로 내부 참조를 그대로 반환하면 외부에서 반환값을
     * 변경해 엔티티 상태를 몰래 바꿀 수 있다(PR #8 리뷰). 생성 시점 deepCopy에 더해 반환
     * 시점에도 방어적으로 복사한다.
     */
    public JsonNode getUnderstoodConcepts() {
        return understoodConcepts.deepCopy();
    }

    public JsonNode getExplanationGaps() {
        return explanationGaps.deepCopy();
    }

    public JsonNode getWeakConcepts() {
        return weakConcepts.deepCopy();
    }

    public JsonNode getSyntaxToImprove() {
        return syntaxToImprove == null ? null : syntaxToImprove.deepCopy();
    }

    public JsonNode getRecommendedProblems() {
        return recommendedProblems == null ? null : recommendedProblems.deepCopy();
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

}
