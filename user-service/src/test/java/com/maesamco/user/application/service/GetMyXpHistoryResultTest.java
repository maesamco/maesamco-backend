package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.entity.XpSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetMyXpHistoryResultTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final Instant EARNED_AT =
            Instant.parse(
                    "2026-09-17T01:20:30Z"
            );

    @Test
    @DisplayName(
            "XP 이력에서 외부 공개 필드만 조회 결과로 매핑한다"
    )
    void itemFrom_mapsPublicFields() {
        XpHistory history =
                createHistory();

        GetMyXpHistoryItemResult result =
                GetMyXpHistoryItemResult.from(
                        history
                );

        assertThat(result.rewardType())
                .isEqualTo(
                        RewardType.FIRST_CORRECT
                );

        assertThat(result.amount())
                .isEqualTo(10);

        assertThat(result.balanceAfter())
                .isEqualTo(120L);

        assertThat(result.description())
                .isEqualTo(
                        "문제 최초 정답 보상"
                );

        assertThat(result.earnedAt())
                .isEqualTo(
                        EARNED_AT
                );
    }

    @Test
    @DisplayName(
            "단일 XP 이력 결과는 외부 공개 필드만 가진다"
    )
    void itemResult_exposesOnlyPublicFields() {
        assertThat(
                Arrays.stream(
                                GetMyXpHistoryItemResult.class
                                        .getRecordComponents()
                        )
                        .map(
                                RecordComponent::getName
                        )
                        .toList()
        )
                .containsExactly(
                        "rewardType",
                        "amount",
                        "balanceAfter",
                        "description",
                        "earnedAt"
                )
                .doesNotContain(
                        "id",
                        "userId",
                        "sourceEventId",
                        "sourceType",
                        "sourceId",
                        "problemId",
                        "rewardDate",
                        "createdAt"
                );
    }

    @Test
    @DisplayName(
            "XP 이력 페이지 결과는 items, nextCursor, hasNext만 가진다"
    )
    void pageResult_exposesOnlyPageFields() {
        assertThat(
                Arrays.stream(
                                GetMyXpHistoriesResult.class
                                        .getRecordComponents()
                        )
                        .map(
                                RecordComponent::getName
                        )
                        .toList()
        ).containsExactly(
                "items",
                "nextCursor",
                "hasNext"
        );
    }

    @Test
    @DisplayName(
            "XP 이력 페이지는 입력 목록을 방어적으로 복사한다"
    )
    void pageResult_defensivelyCopiesItems() {
        List<GetMyXpHistoryItemResult> source =
                new ArrayList<>();

        source.add(
                GetMyXpHistoryItemResult.from(
                        createHistory()
                )
        );

        GetMyXpHistoriesResult result =
                new GetMyXpHistoriesResult(
                        source,
                        null,
                        false
                );

        source.clear();

        assertThat(result.items())
                .hasSize(1);

        assertThatThrownBy(
                () -> result.items()
                        .clear()
        )
                .isInstanceOf(
                        UnsupportedOperationException.class
                );
    }

    @Test
    @DisplayName(
            "XP 이력이 null이면 단일 조회 결과를 생성하지 않는다"
    )
    void itemFrom_rejectsNullHistory() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                GetMyXpHistoryItemResult
                                        .from(null)
                )
                .withMessage(
                        "XP 이력은 필수입니다."
                );
    }

    @Test
    @DisplayName(
            "XP 이력 응답 목록이 null이면 페이지 결과를 생성하지 않는다"
    )
    void pageResult_rejectsNullItems() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new GetMyXpHistoriesResult(
                                        null,
                                        null,
                                        false
                                )
                )
                .withMessage(
                        "XP 이력 응답 목록은 필수입니다."
                );
    }

    private XpHistory createHistory() {
        return XpHistory.create(
                USER_ID,
                UUID.randomUUID(),
                RewardType.FIRST_CORRECT,
                XpSourceType.SUBMISSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                10,
                120L,
                null,
                "문제 최초 정답 보상",
                EARNED_AT
        );
    }
}
