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
 * 오답 단계별 힌트(1~4단계) — 매삼코 DB 테이블 명세 2절.
 *
 * BaseEntity 미적용(불변 보존형, 팀 컨벤션 16절) — 상위 엔티티 CoachingSession의 불변성을
 * 물려받아 생성 후 수정·삭제되지 않는다. coachingSessionId는 같은 서비스 내부 물리 FK지만,
 * 조회 위주로만 쓰여서 @ManyToOne 없이 raw UUID 컬럼으로 유지한다(이슈 #16 결정 — 지연 로딩·N+1
 * 관리 부담 없이 필요할 때만 CoachingSessionRepository로 명시적으로 조회).
 *
 * ⚠️ raw UUID 컬럼이라 JPA로는 이 FK 제약이 DDL에 생성되지 않는다(@ManyToOne/@JoinColumn이
 * 있어야 Hibernate가 FK를 만든다). CHECK 제약과 마찬가지로 Flyway 마이그레이션 스크립트에
 * coaching_session_id → p_coaching_sessions.id FK 제약을 명시적으로 포함시켜야 한다(이슈 #10).
 *
 * TODO(#10): Flyway 마이그레이션 도입 시 p_hints에 아래 두 제약 추가.
 *            1) coaching_session_id에 REFERENCES p_coaching_sessions(id) FK 제약.
 *            2) stage에 CHECK (stage BETWEEN 1 AND 4) — 생성자 검증(requireValidStage)을
 *               우회하는 직접 SQL 입력도 DB 레벨에서 차단.
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

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Builder
    private Hint(UUID coachingSessionId, int stage, String content) {
        this.coachingSessionId = requireNonNull(coachingSessionId, "코칭 세션 ID");
        this.stage = requireValidStage(stage);
        this.content = requireText(content, "힌트 본문");
    }

    public static Hint create(UUID coachingSessionId, int stage, String content) {
        return Hint.builder()
                .coachingSessionId(coachingSessionId)
                .stage(stage)
                .content(content)
                .build();
    }

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
