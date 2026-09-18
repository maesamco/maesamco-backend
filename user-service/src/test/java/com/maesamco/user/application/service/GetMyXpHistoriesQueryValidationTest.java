package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetMyXpHistoriesQueryValidationTest {

    @Test
    @DisplayName(
            "size를 생략하면 기본 조회 크기 20을 적용한다"
    )
    void of_appliesDefaultSize() {
        GetMyXpHistoriesQuery query =
                GetMyXpHistoriesQuery.of(
                        null,
                        null
                );

        assertThat(query.size())
                .isEqualTo(20);

        assertThat(query.cursor())
                .isNull();

        assertThat(query.hasCursor())
                .isFalse();
    }

    @Test
    @DisplayName(
            "명시한 size와 cursor를 유지한다"
    )
    void of_preservesExplicitValues() {
        GetMyXpHistoriesQuery query =
                GetMyXpHistoriesQuery.of(
                        30,
                        "validCursor"
                );

        assertThat(query.size())
                .isEqualTo(30);

        assertThat(query.cursor())
                .isEqualTo("validCursor");

        assertThat(query.hasCursor())
                .isTrue();
    }

    @Test
    @DisplayName(
            "size 경계값 1과 100을 허용한다"
    )
    void constructor_acceptsBoundarySizes() {
        GetMyXpHistoriesQuery minimum =
                new GetMyXpHistoriesQuery(
                        1,
                        null
                );

        GetMyXpHistoriesQuery maximum =
                new GetMyXpHistoriesQuery(
                        100,
                        null
                );

        assertThat(minimum.size())
                .isEqualTo(1);

        assertThat(maximum.size())
                .isEqualTo(100);
    }

    @Test
    @DisplayName(
            "size가 0이면 INVALID_INPUT_VALUE를 반환한다"
    )
    void constructor_rejectsSizeBelowMinimum() {
        assertInvalidQuery(
                () -> new GetMyXpHistoriesQuery(
                        0,
                        null
                )
        );
    }

    @Test
    @DisplayName(
            "size가 101이면 INVALID_INPUT_VALUE를 반환한다"
    )
    void constructor_rejectsSizeAboveMaximum() {
        assertInvalidQuery(
                () -> new GetMyXpHistoriesQuery(
                        101,
                        null
                )
        );
    }

    @Test
    @DisplayName(
            "빈 cursor는 INVALID_INPUT_VALUE를 반환한다"
    )
    void constructor_rejectsEmptyCursor() {
        assertInvalidQuery(
                () -> new GetMyXpHistoriesQuery(
                        20,
                        ""
                )
        );
    }

    @Test
    @DisplayName(
            "공백 cursor는 INVALID_INPUT_VALUE를 반환한다"
    )
    void constructor_rejectsBlankCursor() {
        assertInvalidQuery(
                () -> new GetMyXpHistoriesQuery(
                        20,
                        "   "
                )
        );
    }

    @Test
    @DisplayName(
            "허용 길이를 초과한 cursor는 INVALID_INPUT_VALUE를 반환한다"
    )
    void constructor_rejectsOversizedCursor() {
        String oversizedCursor =
                "a".repeat(
                        XpHistoryCursor.MAX_ENCODED_LENGTH + 1
                );

        assertInvalidQuery(
                () -> new GetMyXpHistoriesQuery(
                        20,
                        oversizedCursor
                )
        );
    }

    private void assertInvalidQuery(
            Runnable invocation
    ) {
        assertThatThrownBy(invocation::run)
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
