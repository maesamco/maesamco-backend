package com.maesamco.user.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * XP 이력 keyset pagination에 사용하는 내부 cursor 값입니다.
 *
 * @param earnedAt 마지막으로 반환한 XP 획득 시각
 * @param xpHistoryId 동일 시각 정렬을 위한 XP 이력 식별자
 */
public record XpHistoryCursor(
        Instant earnedAt,
        UUID xpHistoryId
) {

    static final int MAX_ENCODED_LENGTH = 256;

    /**
     * cursor 구성값을 검증합니다.
     */
    public XpHistoryCursor {
        Objects.requireNonNull(
                earnedAt,
                "XP 획득 시각은 필수입니다."
        );
        Objects.requireNonNull(
                xpHistoryId,
                "XP 이력 식별자는 필수입니다."
        );
    }
}
