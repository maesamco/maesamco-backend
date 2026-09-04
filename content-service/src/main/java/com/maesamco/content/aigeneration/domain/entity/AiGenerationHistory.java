package com.maesamco.content.aigeneration.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "p_ai_generation_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiGenerationHistory {

    private static final int INITIAL_RETRY_COUNT = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 30)
    private AiGenerationPurpose purpose;

    @Column(name = "related_id", updatable = false)
    private UUID relatedId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_context", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> requestContext;

    @Column(name = "model_name", nullable = false, updatable = false, length = 50)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, updatable = false, length = 20)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, updatable = false, length = 20)
    private AiGenerationRequestStatus requestStatus;

    @Column(name = "called_at", nullable = false, updatable = false)
    private Instant calledAt;

    @Column(name = "response_time_ms", updatable = false)
    private Integer responseTimeMs;

    @Column(name = "token_usage", updatable = false)
    private Integer tokenUsage;

    @Column(name = "failure_reason", updatable = false, columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", nullable = false, updatable = false)
    private int retryCount;

    private AiGenerationHistory(
            AiGenerationPurpose purpose,
            UUID relatedId,
            Map<String, Object> requestContext,
            String modelName,
            String promptVersion,
            AiGenerationRequestStatus requestStatus,
            Instant calledAt,
            Integer responseTimeMs,
            Integer tokenUsage,
            String failureReason
    ) {
        this.purpose = Objects.requireNonNull(purpose, "AI 생성 목적은 필수입니다.");
        this.relatedId = relatedId;
        this.requestContext = Map.copyOf(Objects.requireNonNull(requestContext, "AI 요청 문맥은 필수입니다."));
        this.modelName = requireText(modelName, "AI 모델명");
        this.promptVersion = requireText(promptVersion, "프롬프트 버전");
        this.requestStatus = Objects.requireNonNull(requestStatus, "AI 요청 상태는 필수입니다.");
        this.calledAt = Objects.requireNonNull(calledAt, "AI 호출 시각은 필수입니다.");
        this.responseTimeMs = requireNonNegative(responseTimeMs, "AI 응답 시간");
        this.tokenUsage = requireNonNegative(tokenUsage, "AI 토큰 사용량");
        this.failureReason = failureReason;
        this.retryCount = INITIAL_RETRY_COUNT;
    }

    public static AiGenerationHistory success(
            AiGenerationPurpose purpose,
            UUID relatedId,
            Map<String, Object> requestContext,
            String modelName,
            String promptVersion,
            Instant calledAt,
            Integer responseTimeMs,
            Integer tokenUsage
    ) {
        return new AiGenerationHistory(
                purpose,
                Objects.requireNonNull(relatedId, "성공한 AI 생성 이력의 연관 ID는 필수입니다."),
                requestContext,
                modelName,
                promptVersion,
                AiGenerationRequestStatus.SUCCESS,
                calledAt,
                responseTimeMs,
                tokenUsage,
                null
        );
    }

    public static AiGenerationHistory failed(
            AiGenerationPurpose purpose,
            Map<String, Object> requestContext,
            String modelName,
            String promptVersion,
            Instant calledAt,
            Integer responseTimeMs,
            Integer tokenUsage,
            String failureReason
    ) {
        return new AiGenerationHistory(
                purpose,
                null,
                requestContext,
                modelName,
                promptVersion,
                AiGenerationRequestStatus.FAILED,
                calledAt,
                responseTimeMs,
                tokenUsage,
                requireText(failureReason, "AI 생성 실패 원인")
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }
        return value;
    }

    private static Integer requireNonNegative(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(fieldName + "은(는) 0 이상이어야 합니다.");
        }
        return value;
    }
}
