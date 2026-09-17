package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.XpHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;

/**
 * 외부에 공개하는 단일 XP 이력입니다.
 *
 * @param rewardType XP 지급·차감 사유
 * @param amount XP 증감량
 * @param balanceAfter 반영 직후 누적 XP
 * @param description 부가 설명
 * @param earnedAt XP 반영 시각
 */
@Schema(
        name = "GetMyXpHistoryItemResult",
        description = "로그인 사용자의 단일 XP 지급·차감 이력"
)
public record GetMyXpHistoryItemResult(

        @Schema(
                description = "XP 지급·차감 사유",
                example = "FIRST_CORRECT",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        RewardType rewardType,

        @Schema(
                description = "XP 증감량. 지급은 양수, 회수는 음수",
                example = "10",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int amount,

        @Schema(
                description = "해당 이력 반영 직후 누적 XP",
                example = "120",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long balanceAfter,

        @Schema(
                description = "XP 지급·차감 부가 설명",
                example = "문제 최초 정답 보상",
                nullable = true
        )
        String description,

        @Schema(
                description = "XP가 실제 업무에 반영된 UTC 시각",
                example = "2026-09-17T01:20:30Z",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant earnedAt
) {

    /**
     * 필수 공개 필드를 검증합니다.
     */
    public GetMyXpHistoryItemResult {
        Objects.requireNonNull(
                rewardType,
                "XP 보상 유형은 필수입니다."
        );

        Objects.requireNonNull(
                earnedAt,
                "XP 획득 시각은 필수입니다."
        );
    }

    /**
     * XP 이력 엔티티에서 외부 공개 필드만 복사합니다.
     */
    public static GetMyXpHistoryItemResult from(
            XpHistory history
    ) {
        Objects.requireNonNull(
                history,
                "XP 이력은 필수입니다."
        );

        return new GetMyXpHistoryItemResult(
                history.getRewardType(),
                history.getAmount(),
                history.getBalanceAfter(),
                history.getDescription(),
                history.getEarnedAt()
        );
    }
}
