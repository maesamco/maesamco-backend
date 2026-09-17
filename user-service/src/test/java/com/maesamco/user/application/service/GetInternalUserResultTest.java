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

class GetInternalUserResultTest {

    @Test
    @DisplayName("관심 개념 ID 목록을 불변 복사한다")
    void copiesInterestConceptIds() {
        UUID conceptId =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        List<UUID> source =
                new ArrayList<>(
                        List.of(conceptId)
                );

        GetInternalUserResult result =
                new GetInternalUserResult(
                        source
                );

        source.clear();

        assertThat(result.interestConceptIds())
                .containsExactly(conceptId);

        assertThatThrownBy(
                () -> result.interestConceptIds()
                        .add(UUID.randomUUID())
        ).isInstanceOf(
                UnsupportedOperationException.class
        );
    }

    @Test
    @DisplayName("빈 관심 개념 ID 목록을 허용한다")
    void acceptsEmptyInterestConceptIds() {
        GetInternalUserResult result =
                new GetInternalUserResult(
                        List.of()
                );

        assertThat(result.interestConceptIds())
                .isEmpty();
    }

    @Test
    @DisplayName("관심 개념 ID 목록이 null이면 거부한다")
    void rejectsNullInterestConceptIds() {
        assertInvalid(
                () -> new GetInternalUserResult(
                        null
                )
        );
    }

    @Test
    @DisplayName("관심 개념 ID 목록의 null 요소를 거부한다")
    void rejectsNullInterestConceptId() {
        assertInvalid(
                () -> new GetInternalUserResult(
                        Collections.singletonList(
                                null
                        )
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
