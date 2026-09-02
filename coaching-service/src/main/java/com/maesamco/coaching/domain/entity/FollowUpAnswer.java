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
 * 역질문에 대한 사용자 답변 — 매삼코 DB 테이블 명세 5절.
 *
 * BaseEntity 미적용(생성 후 수정 없음, 팀 컨벤션 16절) — 상위 엔티티 FollowUpQuestion에 종속된다.
 * 미답변 상태는 행 자체가 없음 — 사용자가 나중에 이어서 답변 가능하다(서비스요약 [4]-3절). 이 행이
 * 생성되는 시점 자체가 곧 답변 시각이라 별도 created_at 없이 answeredAt 하나만 @CreatedDate로 둔다.
 *
 * followUpQuestionId는 같은 서비스 내부 물리 FK지만, Hint/Explanation/FollowUpQuestion과
 * 동일한 이유로 @ManyToOne 없이 raw UUID 컬럼으로 유지한다(지연 로딩·N+1 관리 부담 없이 필요할
 * 때만 FollowUpQuestionRepository로 명시적으로 조회).
 *
 * raw UUID 컬럼이라 JPA로는 FK 제약이 DDL에 안 생기지만(@ManyToOne/@JoinColumn이 있어야
 * Hibernate가 FK를 만든다), Flyway V1 베이스라인(PR #29)이 follow_up_question_id →
 * p_follow_up_questions.id FK와 UNIQUE(follow_up_question_id)를 실제 마이그레이션
 * 스크립트로 갖고 있어 운영 스키마에도 반영돼 있다(이슈 #10 해결).
 */
@Entity
@Table(
        name = "p_follow_up_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follow_up_answers_question",
                columnNames = {"follow_up_question_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class FollowUpAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "follow_up_question_id", updatable = false, nullable = false)
    private UUID followUpQuestionId;

    @Column(name = "answer_text", updatable = false, nullable = false, columnDefinition = "TEXT")
    private String answerText;

    /*
     * TODO: answered_at이 실제로 TIMESTAMPTZ 컬럼으로 생성되는지 검증하는 회귀 테스트가
     * 없다(BaseEntity처럼 information_schema.columns.data_type을 직접 확인하는 테스트,
     * PR #11에서 BaseEntity 쪽에 이미 지적된 것과 같은 성격 — 이 엔티티는 BaseEntity를
     * 상속하지 않아 별도로 필요). 누군가 실수로 Instant를 LocalDateTime으로 되돌려도
     * 지금은 CI가 못 잡아낸다. Repository 통합 테스트에 추가할 것.
     */
    @CreatedDate
    @Column(name = "answered_at", updatable = false, nullable = false)
    private Instant answeredAt;

    @Builder
    private FollowUpAnswer(UUID followUpQuestionId, String answerText) {
        this.followUpQuestionId = Validate.requireNonNull(followUpQuestionId, "역질문 ID");
        this.answerText = Validate.requireText(answerText, "답변 내용");
    }

    public static FollowUpAnswer create(UUID followUpQuestionId, String answerText) {
        return FollowUpAnswer.builder()
                .followUpQuestionId(followUpQuestionId)
                .answerText(answerText)
                .build();
    }
}
