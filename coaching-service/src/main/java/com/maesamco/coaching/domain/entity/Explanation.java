package com.maesamco.coaching.domain.entity;

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
 * ⚠️ raw UUID 컬럼이라 JPA로는 이 FK 제약이 DDL에 생성되지 않는다(@ManyToOne/@JoinColumn이
 * 있어야 Hibernate가 FK를 만든다). UNIQUE(coaching_session_id) 제약도 지금 @Table로 JPA/테스트
 * 레벨에만 있고, 운영 환경은 ddl-auto=validate라 Flyway 마이그레이션 스크립트가 유일한 스키마
 * 소스가 된다.
 *
 * TODO(#10): Flyway 마이그레이션 도입 시 p_explanations에 아래 두 제약 추가.
 *            1) coaching_session_id에 REFERENCES p_coaching_sessions(id) FK 제약.
 *            2) UNIQUE(coaching_session_id) — 지금 JPA 애노테이션에만 있는 제약을
 *               마이그레이션 스크립트에도 명시적으로 포함.
 */
@Entity
@Table(
        name = "p_explanations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_explanations_session",
                columnNames = {"coaching_session_id"}
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
    private Explanation(UUID coachingSessionId, String content) {
        this.coachingSessionId = requireNonNull(coachingSessionId, "코칭 세션 ID");
        this.content = requireText(content, "설명 본문");
    }

    public static Explanation create(UUID coachingSessionId, String content) {
        return Explanation.builder()
                .coachingSessionId(coachingSessionId)
                .content(content)
                .build();
    }

    // TODO: CoachingSession.java/Hint.java에도 거의 동일한 requireNonNull이 따로
    //       구현돼 있다(User Service의 User/UserInterestConcept와 같은 중복 패턴).
    //       지금은 엔티티가 3개뿐이라 문제 없지만, coaching-service에 엔티티가 더
    //       추가되면 global/util 공통 Validate 유틸로 추출을 고려할 것.
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
