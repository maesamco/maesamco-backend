package com.maesamco.content.dailyquiz.application.result;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * User Service에서 cursor 방식으로 조회한 Daily Quiz 대상 사용자 페이지
 */
public record DailyQuizTargetUserPage(
        // 현재 페이지의 대상 사용자 ID 목록
        List<UUID> userIds,
        // 다음 페이지 조회에 사용할 cursor
        UUID nextCursor,
        // 다음 페이지 존재 여부
        boolean hasNext
) {

    public DailyQuizTargetUserPage {
        userIds = List.copyOf(Objects.requireNonNull(userIds, "대상 사용자 ID 목록은 필수입니다."));

        if (hasNext) {
            if (userIds.isEmpty()) {
                throw new IllegalArgumentException("다음 페이지가 있으면 대상 사용자 ID 목록은 비어 있을 수 없습니다.");
            }

            if (nextCursor == null) {
                throw new IllegalArgumentException("다음 페이지가 있으면 nextCursor는 필수입니다.");
            }

            UUID lastUserId = userIds.getLast();
            if (!nextCursor.equals(lastUserId)) {
                throw new IllegalArgumentException("nextCursor는 현재 페이지의 마지막 사용자 ID여야 합니다.");
            }
        } else if (nextCursor != null) {
            throw new IllegalArgumentException("다음 페이지가 없으면 nextCursor는 null이어야 합니다.");
        }
    }
}
