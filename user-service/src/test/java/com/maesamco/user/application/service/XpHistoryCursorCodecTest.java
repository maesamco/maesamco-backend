package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XpHistoryCursorCodecTest {

    private static final Instant EARNED_AT =
            Instant.parse(
                    "2026-09-17T01:20:30Z"
            );

    private static final UUID XP_HISTORY_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private XpHistoryCursorCodec cursorCodec;

    @BeforeEach
    void setUp() {
        cursorCodec =
                new XpHistoryCursorCodec();
    }

    @Test
    @DisplayName(
            "cursor를 Base64URL로 인코딩하고 원래 값으로 복원한다"
    )
    void encodeAndDecode_roundTrip() {
        XpHistoryCursor original =
                new XpHistoryCursor(
                        EARNED_AT,
                        XP_HISTORY_ID
                );

        String encoded =
                cursorCodec.encode(
                        original
                );

        XpHistoryCursor decoded =
                cursorCodec.decode(
                        encoded
                );

        assertThat(decoded)
                .isEqualTo(original);

        assertThat(encoded)
                .matches(
                        "^[A-Za-z0-9_-]+$"
                )
                .doesNotContain("=");
    }

    @Test
    @DisplayName(
            "동일한 cursor 값은 항상 동일한 문자열로 인코딩한다"
    )
    void encode_isDeterministic() {
        XpHistoryCursor cursor =
                new XpHistoryCursor(
                        EARNED_AT,
                        XP_HISTORY_ID
                );

        assertThat(
                cursorCodec.encode(cursor)
        ).isEqualTo(
                cursorCodec.encode(cursor)
        );
    }

    @Test
    @DisplayName(
            "null cursor는 INVALID_INPUT_VALUE를 반환한다"
    )
    void decode_rejectsNullCursor() {
        assertInvalidCursor(
                null
        );
    }

    @Test
    @DisplayName(
            "빈 cursor는 INVALID_INPUT_VALUE를 반환한다"
    )
    void decode_rejectsEmptyCursor() {
        assertInvalidCursor(
                ""
        );
    }

    @Test
    @DisplayName(
            "Base64URL 문자 외의 문자가 포함된 cursor를 거부한다"
    )
    void decode_rejectsInvalidBase64UrlCharacters() {
        assertInvalidCursor(
                "invalid+cursor/value="
        );
    }

    @Test
    @DisplayName(
            "허용 길이를 초과한 cursor를 거부한다"
    )
    void decode_rejectsOversizedCursor() {
        assertInvalidCursor(
                "a".repeat(
                        XpHistoryCursor.MAX_ENCODED_LENGTH + 1
                )
        );
    }

    @Test
    @DisplayName(
            "지원하지 않는 cursor 버전을 거부한다"
    )
    void decode_rejectsUnsupportedVersion() {
        assertInvalidCursor(
                encodePayload(
                        "v2|"
                                + EARNED_AT
                                + "|"
                                + XP_HISTORY_ID
                )
        );
    }

    @Test
    @DisplayName(
            "필드 개수가 올바르지 않은 cursor를 거부한다"
    )
    void decode_rejectsInvalidPartCount() {
        assertInvalidCursor(
                encodePayload(
                        "v1|"
                                + EARNED_AT
                )
        );
    }

    @Test
    @DisplayName(
            "Instant를 복원할 수 없는 cursor를 거부한다"
    )
    void decode_rejectsInvalidInstant() {
        assertInvalidCursor(
                encodePayload(
                        "v1|invalid-instant|"
                                + XP_HISTORY_ID
                )
        );
    }

    @Test
    @DisplayName(
            "UUID를 복원할 수 없는 cursor를 거부한다"
    )
    void decode_rejectsInvalidUuid() {
        assertInvalidCursor(
                encodePayload(
                        "v1|"
                                + EARNED_AT
                                + "|invalid-uuid"
                )
        );
    }

    @Test
    @DisplayName(
            "잘못된 cursor 원문을 예외 메시지에 노출하지 않는다"
    )
    void decode_doesNotExposeOriginalCursor() {
        String originalCursor =
                "sensitive-invalid-cursor";

        assertThatThrownBy(
                () -> cursorCodec.decode(
                        originalCursor
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(
                                    exception.getErrorCode()
                            ).isEqualTo(
                                    ErrorCode.INVALID_INPUT_VALUE
                            );

                            assertThat(
                                    exception.getMessage()
                            ).doesNotContain(
                                    originalCursor
                            );
                        }
                );
    }

    private String encodePayload(
            String payload
    ) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        payload.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
    }

    private void assertInvalidCursor(
            String cursor
    ) {
        assertThatThrownBy(
                () -> cursorCodec.decode(
                        cursor
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
    @DisplayName(
            "애플리케이션 지원 범위를 벗어난 미래 시각 cursor를 거부한다"
    )
    void decode_rejectsInstantAfterSupportedRange() {
        assertInvalidCursor(
                encodePayload(
                        "v1|"
                                + Instant.MAX
                                + "|"
                                + XP_HISTORY_ID
                )
        );
    }

    @Test
    @DisplayName(
            "애플리케이션 지원 범위를 벗어난 과거 시각 cursor를 거부한다"
    )
    void decode_rejectsInstantBeforeSupportedRange() {
        assertInvalidCursor(
                encodePayload(
                        "v1|"
                                + Instant.MIN
                                + "|"
                                + XP_HISTORY_ID
                )
        );
    }

    @Test
    @DisplayName(
            "서버 형식과 다른 비표준 Instant 표현을 거부한다"
    )
    void decode_rejectsNonCanonicalInstant() {
        assertInvalidCursor(
                encodePayload(
                        "v1|2026-09-17T01:20:30.000Z|"
                                + XP_HISTORY_ID
                )
        );
    }

    @Test
    @DisplayName(
            "서버 형식과 다른 비표준 UUID 표현을 거부한다"
    )
    void decode_rejectsNonCanonicalUuid() {
        assertInvalidCursor(
                encodePayload(
                        "v1|"
                                + EARNED_AT
                                + "|1-1-1-1-1"
                )
        );
    }
}
