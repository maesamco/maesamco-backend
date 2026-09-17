package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;

/**
 * 로그인 사용자의 XP 이력 조회 조건입니다.
 *
 * @param size 페이지당 조회 개수
 * @param cursor 다음 페이지 조회 cursor
 */
public record GetMyXpHistoriesQuery(
        int size,
        String cursor
) {

    public static final int DEFAULT_SIZE = 20;

    public static final int MIN_SIZE = 1;

    public static final int MAX_SIZE = 100;

    /**
     * 조회 조건을 검증합니다.
     */
    public GetMyXpHistoriesQuery {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "XP 이력 조회 크기는 1 이상 100 이하여야 합니다."
            );
        }

        if (cursor != null
                && (
                cursor.isBlank()
                        || cursor.length()
                        > XpHistoryCursor.MAX_ENCODED_LENGTH
        )) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "XP 이력 조회 cursor 형식이 올바르지 않습니다."
            );
        }
    }

    /**
     * 선택적인 size 입력에 기본값을 적용합니다.
     */
    public static GetMyXpHistoriesQuery of(
            Integer size,
            String cursor
    ) {
        return new GetMyXpHistoriesQuery(
                size == null
                        ? DEFAULT_SIZE
                        : size,
                cursor
        );
    }

    /**
     * 후속 페이지 cursor가 존재하는지 반환합니다.
     */
    public boolean hasCursor() {
        return cursor != null;
    }
}
