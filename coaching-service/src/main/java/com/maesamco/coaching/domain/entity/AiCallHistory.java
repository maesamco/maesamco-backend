package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * 힌트·역질문·피드백 AI 호출 이력 — 매삼코 DB 테이블 명세 05-8절.
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
 * ⚠️ raw UUID 컬럼이라 JPA로는 이 FK 제약이 DDL에 생성되지 않는다. purpose의
 * CHECK(purpose IN ('HINT','FOLLOWUP_QUESTION','FEEDBACK'))도 지금 Java enum 레벨에만 있고,
 * 운영 환경은 ddl-auto=validate라 Flyway 마이그레이션 스크립트가 유일한 스키마 소스가 된다.
 *
 * TODO(#10): Flyway 마이그레이션 도입 시 p_ai_call_histories에 아래 두 제약 추가.
 *            1) coaching_session_id에 REFERENCES p_coaching_sessions(id) FK 제약.
 *            2) purpose에 CHECK(purpose IN ('HINT','FOLLOWUP_QUESTION','FEEDBACK')) 제약.
 */
@Entity
@Table(name = "p_ai_call_histories")
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
        this.coachingSessionId = requireNonNull(coachingSessionId, "코칭 세션 ID");
        this.purpose = requireNonNull(purpose, "AI 호출 목적");
        this.modelName = requireText(modelName, "모델명");
        this.promptVersion = requireText(promptVersion, "프롬프트 버전");
        this.requestStatus = requireText(requestStatus, "요청 상태");
        this.responseTimeMs = responseTimeMs;
        this.tokenUsage = tokenUsage;
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

    // TODO: CoachingSession.java 등에도 거의 동일한 requireNonNull이 따로 구현돼 있다(User
    //       Service의 User/UserInterestConcept와 같은 중복 패턴). coaching-service 도메인
    //       구현이 마무리되면 global/util 공통 Validate 유틸로 추출을 고려할 것.
    private static UUID requireNonNull(UUID value, String fieldNameKorean) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + "는 필수입니다.");
        }
        return value;
    }

    private static AiCallPurpose requireNonNull(AiCallPurpose value, String fieldNameKorean) {
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

    private static int requirePositiveOrZero(int value, String fieldNameKorean) {
        if (value < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + "는 0 이상이어야 합니다.");
        }
        return value;
    }
}
