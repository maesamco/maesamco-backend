package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.UserGamificationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GetMyGamificationResultTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Test
    @DisplayName(
            "초기 게이미피케이션 상태를 조회 결과로 매핑한다"
    )
    void from_mapsInitialState() {
        UserGamificationState state =
                UserGamificationState.create(
                        USER_ID
                );

        GetMyGamificationResult result =
                GetMyGamificationResult.from(
                        state
                );

        assertThat(result.totalXp())
                .isZero();

        assertThat(result.level())
                .isEqualTo(1);

        assertThat(result.currentStreak())
                .isZero();

        assertThat(result.longestStreak())
                .isZero();

        assertThat(result.lastActivityDate())
                .isNull();
    }

    @Test
    @DisplayName(
            "누적 XP와 스트릭 상태를 조회 결과로 정확히 매핑한다"
    )
    void from_mapsProgressedState() {
        UserGamificationState state =
                UserGamificationState.create(
                        USER_ID
                );

        state.applyXp(
                120L,
                2
        );

        for (int day = 1; day <= 7; day++) {
            state.recordActivity(
                    LocalDate.of(
                            2026,
                            9,
                            day
                    )
            );
        }

        state.recordActivity(
                LocalDate.of(
                        2026,
                        9,
                        14
                )
        );
        state.recordActivity(
                LocalDate.of(
                        2026,
                        9,
                        15
                )
        );
        state.recordActivity(
                LocalDate.of(
                        2026,
                        9,
                        16
                )
        );

        GetMyGamificationResult result =
                GetMyGamificationResult.from(
                        state
                );

        assertThat(result.totalXp())
                .isEqualTo(120L);

        assertThat(result.level())
                .isEqualTo(2);

        assertThat(result.currentStreak())
                .isEqualTo(3);

        assertThat(result.longestStreak())
                .isEqualTo(7);

        assertThat(result.lastActivityDate())
                .isEqualTo(
                        LocalDate.of(
                                2026,
                                9,
                                16
                        )
                );
    }

    @Test
    @DisplayName(
            "게이미피케이션 조회 결과는 외부 공개 필드만 가진다"
    )
    void exposesOnlyPublicFields() {
        assertThat(
                Arrays.stream(
                                GetMyGamificationResult.class
                                        .getRecordComponents()
                        )
                        .map(
                                RecordComponent::getName
                        )
                        .toList()
        ).containsExactly(
                "totalXp",
                "level",
                "currentStreak",
                "longestStreak",
                "lastActivityDate"
        );
    }

    @Test
    @DisplayName(
            "게이미피케이션 상태가 null이면 결과를 생성하지 않는다"
    )
    void from_rejectsNullState() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                GetMyGamificationResult
                                        .from(null)
                )
                .withMessage(
                        "게이미피케이션 상태는 필수입니다."
                );
    }
}
