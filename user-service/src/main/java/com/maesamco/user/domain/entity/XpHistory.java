package com.maesamco.user.domain.entity;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * XP 지급·차감 결과를 보존하는 불변 이력 엔티티입니다.
 *
 * <p>생성된 이력은 수정하거나 삭제하지 않으며, 원천 서비스의 리소스와
 * JPA 연관관계를 맺지 않고 UUID 값만 저장합니다.</p>
 *
 * <p>{@code sourceEventId}는 Kafka 이벤트의 중복 소비를 확인하는 식별자입니다.
 * {@code problemId}와 {@code rewardDate}는 각각 최초 정답 보상과 일일 목표 보상의
 * 중복 지급 여부를 확인할 때 사용합니다.</p>
 *
 * <p>TODO(#10): Flyway 후속 마이그레이션에 다음 제약과 인덱스를 추가해야 합니다.</p>
 * <ul>
 *     <li>{@code source_event_id IS NOT NULL}인 행의 부분 UNIQUE</li>
 *     <li>최초 정답의 {@code (user_id, problem_id, reward_type)} 부분 UNIQUE</li>
 *     <li>일일 목표의 {@code (user_id, reward_date, reward_type)} 부분 UNIQUE</li>
 *     <li>{@code (user_id, earned_at DESC)} 조회 인덱스</li>
 * </ul>
 */
@Getter
@Entity
@Table(
        name = "p_xp_histories",
        schema = "user_schema"
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class XpHistory implements Persistable<UUID> {

    private static final int DESCRIPTION_MAX_LENGTH = 255;

    /** XP 이력 식별자입니다. */
    @Id
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    /** XP가 반영된 사용자 식별자입니다. */
    @Column(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private UUID userId;

    /** Kafka 이벤트의 중복 소비를 확인하는 식별자입니다. */
    @Column(
            name = "source_event_id",
            updatable = false
    )
    private UUID sourceEventId;

    /** XP 지급·차감 업무 사유입니다. */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "reward_type",
            nullable = false,
            updatable = false,
            length = 30
    )
    private RewardType rewardType;

    /** XP를 발생시킨 원천 리소스 종류입니다. */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "source_type",
            nullable = false,
            updatable = false,
            length = 30
    )
    private XpSourceType sourceType;

    /** 다른 서비스에서 관리하는 원천 리소스 식별자입니다. */
    @Column(
            name = "source_id",
            updatable = false
    )
    private UUID sourceId;

    /** Content Service에서 관리하는 문제 식별자입니다. */
    @Column(
            name = "problem_id",
            updatable = false
    )
    private UUID problemId;

    /** 지급은 양수, 회수는 음수로 기록하는 XP 증감량입니다. */
    @Column(
            name = "amount",
            nullable = false,
            updatable = false
    )
    private int amount;

    /** XP 반영 후 사용자의 누적 XP입니다. */
    @Column(
            name = "balance_after",
            nullable = false,
            updatable = false
    )
    private long balanceAfter;

    /** 일 단위 보상의 중복 지급을 확인하는 Asia/Seoul 기준 날짜입니다. */
    @Column(
            name = "reward_date",
            updatable = false
    )
    private LocalDate rewardDate;

    /** XP 지급·차감 사유에 대한 부가 설명입니다. */
    @Column(
            name = "description",
            updatable = false,
            length = DESCRIPTION_MAX_LENGTH
    )
    private String description;

    /** 업무 이벤트 기준으로 XP가 반영된 시각입니다. */
    @Column(
            name = "earned_at",
            nullable = false,
            updatable = false
    )
    private Instant earnedAt;

    /** XP 이력이 데이터베이스에 생성된 시각입니다. */
    @CreatedDate
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    /** 검증이 완료된 값으로 불변 XP 이력을 생성합니다. */
    private XpHistory(
            UUID id,
            UUID userId,
            UUID sourceEventId,
            RewardType rewardType,
            XpSourceType sourceType,
            UUID sourceId,
            UUID problemId,
            int amount,
            long balanceAfter,
            LocalDate rewardDate,
            String description,
            Instant earnedAt
    ) {
        this.id = requireNonNull(id, "XP 이력 ID는 필수입니다.");
        this.userId = requireNonNull(userId, "사용자 ID는 필수입니다.");
        this.sourceEventId = sourceEventId;
        this.rewardType = requireNonNull(rewardType, "보상 유형은 필수입니다.");
        this.sourceType = requireNonNull(sourceType, "원천 유형은 필수입니다.");
        this.sourceId = sourceId;
        this.problemId = problemId;
        this.amount = validateAmount(amount, rewardType);
        this.balanceAfter = validateBalanceAfter(balanceAfter);
        this.rewardDate = rewardDate;
        this.description = validateDescription(description);
        this.earnedAt = requireNonNull(earnedAt, "XP 획득 시각은 필수입니다.");

        validateRewardReference(rewardType, problemId, rewardDate);
    }

    /**
     * XP 지급·차감 결과를 새 이력으로 생성합니다.
     *
     * @param userId 사용자 식별자
     * @param sourceEventId Kafka 원천 이벤트 식별자
     * @param rewardType 보상 유형
     * @param sourceType 원천 리소스 유형
     * @param sourceId 원천 리소스 식별자
     * @param problemId 문제 식별자
     * @param amount XP 증감량
     * @param balanceAfter 반영 후 누적 XP
     * @param rewardDate 일 단위 보상 기준 날짜
     * @param description 부가 설명
     * @param earnedAt XP 반영 시각
     * @return 생성된 불변 XP 이력
     */
    public static XpHistory create(
            UUID userId,
            UUID sourceEventId,
            RewardType rewardType,
            XpSourceType sourceType,
            UUID sourceId,
            UUID problemId,
            int amount,
            long balanceAfter,
            LocalDate rewardDate,
            String description,
            Instant earnedAt
    ) {
        return new XpHistory(
                UUID.randomUUID(),
                userId,
                sourceEventId,
                rewardType,
                sourceType,
                sourceId,
                problemId,
                amount,
                balanceAfter,
                rewardDate,
                description,
                earnedAt
        );
    }

    /**
     * 직접 할당한 UUID를 사용하는 새 엔티티인지 판단합니다.
     *
     * <p>저장 시각이 아직 없으면 신규 엔티티이므로 Spring Data JPA가
     * {@code merge()} 대신 {@code persist()}를 사용합니다.</p>
     */
    @Override
    @Transient
    public boolean isNew() {
        return createdAt == null;
    }

    /** XP 증감량과 보상 유형의 조합을 검증합니다. */
    private static int validateAmount(
            int amount,
            RewardType rewardType
    ) {
        if (amount == 0) {
            throw invalidInput("XP 증감량은 0일 수 없습니다.");
        }

        if (amount < 0 && rewardType != RewardType.ADMIN_ADJUSTMENT) {
            throw invalidInput("XP 차감은 관리자 조정만 허용됩니다.");
        }

        return amount;
    }

    /** XP 반영 후 잔액이 DB 불변식을 만족하는지 검증합니다. */
    private static long validateBalanceAfter(long balanceAfter) {
        if (balanceAfter < 0) {
            throw invalidInput("XP 반영 후 잔액은 0 이상이어야 합니다.");
        }

        return balanceAfter;
    }

    /** 보상 유형별 중복 확인에 필요한 참조 값이 존재하는지 검증합니다. */
    private static void validateRewardReference(
            RewardType rewardType,
            UUID problemId,
            LocalDate rewardDate
    ) {
        if (rewardType == RewardType.FIRST_CORRECT && problemId == null) {
            throw invalidInput("최초 정답 보상에는 문제 ID가 필수입니다.");
        }

        if (rewardType == RewardType.DAILY_GOAL_COMPLETED
                && rewardDate == null) {
            throw invalidInput("일일 목표 보상에는 보상 날짜가 필수입니다.");
        }
    }

    /** 부가 설명의 최대 길이를 검증합니다. */
    private static String validateDescription(String description) {
        if (description != null
                && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw invalidInput("XP 이력 설명은 255자 이하여야 합니다.");
        }

        return description;
    }

    /** 필수 값이 null인지 검증합니다. */
    private static <T> T requireNonNull(
            T value,
            String message
    ) {
        if (value == null) {
            throw invalidInput(message);
        }

        return value;
    }

    /** 잘못된 입력에 대한 도메인 예외를 생성합니다. */
    private static BusinessException invalidInput(String message) {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                message
        );
    }
}
