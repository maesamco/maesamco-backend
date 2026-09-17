package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Daily Quiz 대상 사용자 cursor 조회 결과입니다.
 *
 * <p>Content Service의 {@code UserQuizTargetPageResponse}와
 * 동일한 JSON 필드 계약을 사용합니다.</p>
 */
public record GetQuizTargetUsersResult(
        List<UUID> userIds,
        UUID nextCursor,
        boolean hasNext
) {

    /**
     * cursor 페이지 응답의 구조적 불변식을 검증합니다.
     */
    public GetQuizTargetUsersResult {
        if (userIds == null) {
            throw invalidResult(
                    "대상 사용자 ID 목록은 필수입니다."
            );
        }

        boolean containsNull =
                userIds
                        .stream()
                        .anyMatch(
                                Objects::isNull
                        );

        if (containsNull) {
            throw invalidResult(
                    "대상 사용자 ID는 null일 수 없습니다."
            );
        }

        userIds =
                List.copyOf(
                        userIds
                );

        if (hasNext) {
            validateNextPage(
                    userIds,
                    nextCursor
            );
        } else if (nextCursor != null) {
            throw invalidResult(
                    "다음 페이지가 없으면 nextCursor는 null이어야 합니다."
            );
        }
    }

    /**
     * 다음 페이지가 존재하는 응답의 cursor 규칙을 검증합니다.
     */
    private static void validateNextPage(
            List<UUID> userIds,
            UUID nextCursor
    ) {
        if (userIds.isEmpty()) {
            throw invalidResult(
                    "다음 페이지가 있으면 사용자 ID 목록은 비어 있을 수 없습니다."
            );
        }

        if (nextCursor == null) {
            throw invalidResult(
                    "다음 페이지가 있으면 nextCursor는 필수입니다."
            );
        }

        UUID lastUserId =
                userIds.getLast();

        if (!nextCursor.equals(lastUserId)) {
            throw invalidResult(
                    "nextCursor는 현재 페이지의 마지막 사용자 ID여야 합니다."
            );
        }
    }

    /**
     * 잘못 구성된 cursor 응답에 사용할 예외를 생성합니다.
     */
    private static BusinessException invalidResult(
            String message
    ) {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                message
        );
    }
}
