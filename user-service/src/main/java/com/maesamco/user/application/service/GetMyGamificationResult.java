package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.UserGamificationState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 로그인 사용자의 현재 게이미피케이션 상태 조회 결과입니다.
 *
 * <p>사용자 식별자, 낙관적 락 버전과 영속성 감사 정보는
 * 외부 응답에 포함하지 않습니다.</p>
 *
 * @param totalXp 누적 XP
 * @param level 현재 레벨
 * @param currentStreak 현재 연속 학습 일수
 * @param longestStreak 최장 연속 학습 일수
 * @param lastActivityDate 마지막 유효 학습 날짜
 */
@Schema(
        name = "GetMyGamificationResult",
        description = "로그인 사용자의 현재 XP, 레벨 및 연속 학습 상태"
)
public record GetMyGamificationResult(

        @Schema(
                description = "누적 XP",
                example = "120",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long totalXp,

        @Schema(
                description = "현재 레벨",
                example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int level,

        @Schema(
                description = "현재 연속 학습 일수",
                example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int currentStreak,

        @Schema(
                description = "최장 연속 학습 일수",
                example = "7",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int longestStreak,

        @Schema(
                description = "마지막 유효 학습 날짜. 학습 활동이 없으면 null",
                example = "2026-09-16",
                nullable = true
        )
        LocalDate lastActivityDate
) {

    /**
     * 도메인 상태에서 외부 조회 결과를 생성합니다.
     */
    public static GetMyGamificationResult from(
            UserGamificationState state
    ) {
        Objects.requireNonNull(
                state,
                "게이미피케이션 상태는 필수입니다."
        );

        return new GetMyGamificationResult(
                state.getTotalXp(),
                state.getLevel(),
                state.getCurrentStreak(),
                state.getLongestStreak(),
                state.getLastActivityDate()
        );
    }
}
