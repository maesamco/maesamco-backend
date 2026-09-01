package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.util.Validate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 힌트·역질문·피드백 AI 호출 이력 — 매삼코 DB 테이블 명세 8절.
 *
 * BaseEntity 미적용(append-only 로그/이력, 팀 컨벤션 16절) — 발생한 AI 호출 사실을 기록할 뿐
 * 수정·삭제 대상이 아니다. updated_at/by가 없다는 게 명세상으로도 확인된다 — 재시도는 기존 행을
 * 갱신하는 게 아니라 그 시도 자체를 나타내는 새 행을 추가하는 것으로(retryCount는 그 행이
 * 몇 번째 시도인지를 나타내는 값), 호출이 끝난 뒤 한 번에 기록한다.
 *
 * coachingSessionId는 같은 서비스 내부 물리 FK지만, Hint/Explanation과 동일한 이유로
 * @ManyToOne 없이 raw UUID 컬럼으로 유지한다(지연 로딩·N+1 관리 부담 없이 필요할 때만
 * CoachingSessionRepository로 명시적으로 조회).
 *
 * 다른 Coaching 자식 테이블과 달리 UNIQUE 제약이 없다 — 세션당 여러 번 AI를 호출할 수 있다.
 *
 * raw UUID 컬럼이라 JPA로는 FK 제약이 DDL에 안 생기지만, Flyway V1 베이스라인(PR #29)이
 * coaching_session_id → p_coaching_sessions.id FK와 purpose에 대한
 * CHECK(purpose IN ('HINT','FOLLOWUP_QUESTION','FEEDBACK'))을 실제 마이그레이션 스크립트로
 * 갖고 있어 운영 스키마에도 반영돼 있다(이슈 #10 해결).
 *
 * coaching_session_id는 FK 컬럼이지만 PostgreSQL은 FK 생성 시 참조 컬럼에 인덱스를 자동으로
 * 만들어주지 않는다 — findByCoachingSessionId()가 누적되는 호출 이력을 이 컬럼으로 필터링하는
 * 주요 조회 경로라 인덱스가 필요하다(PR #8 리뷰). V2 마이그레이션에서 실제로 추가했다(이슈 #10).
 */
@Entity
@Table(
        name = "p_ai_call_histories",
        indexes = @Index(name = "idx_ai_call_histories_coaching_session", columnList = "coaching_session_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AiCallHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "coaching_session_id", updatable = false, nullable = false)
    private UUID coachingSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", updatable = false, nullable = false, length = 30)
    private AiCallPurpose purpose;

    @Column(name = "model_name", updatable = false, nullable = false, length = 50)
    private String modelName;

    @Column(name = "prompt_version", updatable = false, nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "request_status", updatable = false, nullable = false, length = 20)
    private String requestStatus;

    /*
     * TODO: called_at이 실제로 TIMESTAMPTZ 컬럼으로 생성되는지 검증하는 회귀 테스트가
     * 없다(AiFeedback의 information_schema.columns.data_type 검증 테스트와 같은 성격, PR #33).
     * Repository 통합 테스트에 추가할 것.
     */
    @CreatedDate
    @Column(name = "called_at", updatable = false, nullable = false)
    private Instant calledAt;

    @Column(name = "response_time_ms", updatable = false)
    private Integer responseTimeMs;

    @Column(name = "token_usage", updatable = false)
    private Integer tokenUsage;

    @Column(name = "failure_reason", updatable = false, columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", updatable = false, nullable = false)
    private int retryCount;

    @Builder
    private AiCallHistory(
            UUID coachingSessionId, AiCallPurpose purpose, String modelName, String promptVersion,
            String requestStatus, Integer responseTimeMs, Integer tokenUsage, String failureReason,
            int retryCount
    ) {
        this.coachingSessionId = Validate.requireNonNull(coachingSessionId, "코칭 세션 ID");
        this.purpose = Validate.requireNonNull(purpose, "AI 호출 목적");
        this.modelName = Validate.requireText(modelName, 50, "모델명");
        this.promptVersion = Validate.requireText(promptVersion, 20, "프롬프트 버전");
        this.requestStatus = Validate.requireText(requestStatus, 20, "요청 상태");
        this.responseTimeMs = Validate.requireNonNegativeIfPresent(responseTimeMs, "응답 시간");
        this.tokenUsage = Validate.requireNonNegativeIfPresent(tokenUsage, "토큰 사용량");
        this.failureReason = failureReason;
        this.retryCount = requirePositiveOrZero(retryCount, "재시도 횟수");
    }

    public static AiCallHistory create(
            UUID coachingSessionId, AiCallPurpose purpose, String modelName, String promptVersion,
            String requestStatus, Integer responseTimeMs, Integer tokenUsage, String failureReason,
            int retryCount
    ) {
        return AiCallHistory.builder()
                .coachingSessionId(coachingSessionId)
                .purpose(purpose)
                .modelName(modelName)
                .promptVersion(promptVersion)
                .requestStatus(requestStatus)
                .responseTimeMs(responseTimeMs)
                .tokenUsage(tokenUsage)
                .failureReason(failureReason)
                .retryCount(retryCount)
                .build();
    }

    private static int requirePositiveOrZero(int value, String fieldNameKorean) {
        if (value < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + "는 0 이상이어야 합니다.");
        }
        return value;
    }
}
