package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetQuizTargetUsersResultTest {

    private static final UUID FIRST_USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID SECOND_USER_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    @Test
    @DisplayName("다음 페이지가 있으면 마지막 사용자 ID를 cursor로 사용한다")
    void acceptsValidNextPage() {
        List<UUID> source =
                new ArrayList<>(
                        List.of(
                                FIRST_USER_ID,
                                SECOND_USER_ID
                        )
                );

        GetQuizTargetUsersResult result =
                new GetQuizTargetUsersResult(
                        source,
                        SECOND_USER_ID,
                        true
                );

        source.clear();

        assertThat(result.userIds())
                .containsExactly(
                        FIRST_USER_ID,
                        SECOND_USER_ID
                );
        assertThat(result.nextCursor())
                .isEqualTo(SECOND_USER_ID);
        assertThat(result.hasNext())
                .isTrue();

        assertThatThrownBy(
                () -> result.userIds()
                        .add(UUID.randomUUID())
        ).isInstanceOf(
                UnsupportedOperationException.class
        );
    }

    @Test
    @DisplayName("마지막 페이지는 nextCursor 없이 반환한다")
    void acceptsLastPage() {
        GetQuizTargetUsersResult result =
                new GetQuizTargetUsersResult(
                        List.of(FIRST_USER_ID),
                        null,
                        false
                );

        assertThat(result.userIds())
                .containsExactly(FIRST_USER_ID);
        assertThat(result.nextCursor())
                .isNull();
        assertThat(result.hasNext())
                .isFalse();
    }

    @Test
    @DisplayName("빈 마지막 페이지를 허용한다")
    void acceptsEmptyLastPage() {
        GetQuizTargetUsersResult result =
                new GetQuizTargetUsersResult(
                        List.of(),
                        null,
                        false
                );

        assertThat(result.userIds())
                .isEmpty();
        assertThat(result.nextCursor())
                .isNull();
        assertThat(result.hasNext())
                .isFalse();
    }

    @Test
    @DisplayName("사용자 ID 목록이 null이면 거부한다")
    void rejectsNullUserIds() {
        assertInvalid(
                () -> new GetQuizTargetUsersResult(
                        null,
                        null,
                        false
                )
        );
    }

    @Test
    @DisplayName("사용자 ID 목록의 null 요소를 거부한다")
    void rejectsNullUserId() {
        assertInvalid(
                () -> new GetQuizTargetUsersResult(
                        Collections.singletonList(
                                null
                        ),
                        null,
                        false
                )
        );
    }

    @Test
    @DisplayName("다음 페이지가 있는데 사용자 목록이 비어 있으면 거부한다")
    void rejectsEmptyNextPage() {
        assertInvalid(
                () -> new GetQuizTargetUsersResult(
                        List.of(),
                        FIRST_USER_ID,
                        true
                )
        );
    }

    @Test
    @DisplayName("다음 페이지가 있는데 nextCursor가 없으면 거부한다")
    void rejectsMissingNextCursor() {
        assertInvalid(
                () -> new GetQuizTargetUsersResult(
                        List.of(FIRST_USER_ID),
                        null,
                        true
                )
        );
    }

    @Test
    @DisplayName("nextCursor가 마지막 사용자 ID와 다르면 거부한다")
    void rejectsCursorDifferentFromLastUserId() {
        assertInvalid(
                () -> new GetQuizTargetUsersResult(
                        List.of(
                                FIRST_USER_ID,
                                SECOND_USER_ID
                        ),
                        FIRST_USER_ID,
                        true
                )
        );
    }

    @Test
    @DisplayName("마지막 페이지에 nextCursor가 있으면 거부한다")
    void rejectsCursorOnLastPage() {
        assertInvalid(
                () -> new GetQuizTargetUsersResult(
                        List.of(FIRST_USER_ID),
                        FIRST_USER_ID,
                        false
                )
        );
    }

    private void assertInvalid(
            ThrowingCallable callable
    ) {
        assertThatThrownBy(callable)
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
}
