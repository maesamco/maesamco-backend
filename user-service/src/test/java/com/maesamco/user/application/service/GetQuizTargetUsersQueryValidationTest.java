package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetQuizTargetUsersQueryValidationTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 1_000})
    @DisplayName("조회 크기의 최솟값과 최댓값을 허용한다")
    void acceptsBoundarySizes(
            int size
    ) {
        assertThatCode(
                () -> new GetQuizTargetUsersQuery(
                        null,
                        size
                )
        ).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1_001})
    @DisplayName("허용 범위를 벗어난 조회 크기를 거부한다")
    void rejectsOutOfRangeSize(
            int size
    ) {
        assertThatThrownBy(
                () -> new GetQuizTargetUsersQuery(
                        null,
                        size
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode.INVALID_INPUT_VALUE
                                )
                );
    }

    @Test
    @DisplayName("첫 페이지 조회를 위한 null cursor를 허용한다")
    void acceptsNullCursor() {
        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        null,
                        20
                );

        assertThat(query.cursor())
                .isNull();
        assertThat(query.size())
                .isEqualTo(20);
    }

    @Test
    @DisplayName("다음 페이지 cursor를 그대로 보존한다")
    void preservesCursor() {
        UUID cursor =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        cursor,
                        20
                );

        assertThat(query.cursor())
                .isEqualTo(cursor);
    }
}
