package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;

import java.util.UUID;

/**
 * Daily Quiz 대상 사용자 cursor 조회 조건입니다.
 *
 * <p>첫 페이지에서는 {@code cursor}를 전달하지 않으며,
 * 다음 페이지부터 직전 응답의 {@code nextCursor}를 전달합니다.</p>
 */
public record GetQuizTargetUsersQuery(
        UUID cursor,
        int size
) {

    public static final int MIN_SIZE = 1;

    public static final int MAX_SIZE = 1_000;

    /**
     * 조회 크기가 내부 API 계약의 허용 범위인지 검증합니다.
     */
    public GetQuizTargetUsersQuery {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "Daily Quiz 대상 사용자 조회 크기는 "
                            + MIN_SIZE
                            + " 이상 "
                            + MAX_SIZE
                            + " 이하여야 합니다."
            );
        }
    }
}
