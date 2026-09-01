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
 * AI 역질문 — 매삼코 DB 테이블 명세 4절.
 *
 * BaseEntity 미적용 — 상위 엔티티 Explanation에 종속된 자식 레코드로, Hint/Explanation과
 * 동일하게 생성 후 수정·삭제되지 않는다(팀 컨벤션 16절). explanationId는 같은 서비스 내부
 * 물리 FK지만, Hint/Explanation과 동일한 이유로 @ManyToOne 없이 raw UUID 컬럼으로 유지한다
 * (지연 로딩·N+1 관리 부담 없이 필요할 때만 ExplanationRepository로 명시적으로 조회).
 *
 * MVP는 설명 1건당 역질문 1건(서비스요약 6절).
 *
 * raw UUID 컬럼이라 JPA로는 FK 제약이 DDL에 안 생기지만(@ManyToOne/@JoinColumn이 있어야
 * Hibernate가 FK를 만든다), Flyway V1 베이스라인(PR #29)이 explanation_id →
 * p_explanations.id FK와 UNIQUE(explanation_id)를 실제 마이그레이션 스크립트로 갖고 있어
 * 운영 스키마에도 반영돼 있다(이슈 #10 해결).
 */
@Entity
@Table(
        name = "p_follow_up_questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follow_up_questions_explanation",
                columnNames = {"explanation_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class FollowUpQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "explanation_id", updatable = false, nullable = false)
    private UUID explanationId;

    @Column(name = "question_text", updatable = false, nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /**
     * 역질문 분류(동작원리/선택이유/경계값/자료구조/다른해법/복잡도 등) — 명세상 nullable이고
     * CHECK 제약이 없어 자유 문자열로 둔다.
     */
    @Column(name = "category", updatable = false, length = 30)
    private String category;

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
    private FollowUpQuestion(UUID explanationId, String questionText, String category) {
        this.explanationId = Validate.requireNonNull(explanationId, "설명 ID");
        this.questionText = Validate.requireText(questionText, "질문 내용");
        this.category = category;
    }

    public static FollowUpQuestion create(UUID explanationId, String questionText, String category) {
        return FollowUpQuestion.builder()
                .explanationId(explanationId)
                .questionText(questionText)
                .category(category)
                .build();
    }
}
