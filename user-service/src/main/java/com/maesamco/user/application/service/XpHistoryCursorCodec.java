package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * XP 이력 pagination cursor를 버전화된 Base64URL 문자열로 변환합니다.
 */
@Component
public class XpHistoryCursorCodec {

    private static final String VERSION = "v1";

    private static final String DELIMITER = "|";

    private static final String SPLIT_DELIMITER = "\\|";

    private static final int PAYLOAD_PART_COUNT = 3;

    /**
     * PostgreSQL TIMESTAMPTZ에 안전하게 전달할 수 있도록
     * 애플리케이션에서 지원하는 cursor 시각 범위를 제한합니다.
     */
    private static final Instant MIN_SUPPORTED_EARNED_AT =
            Instant.parse(
                    "0001-01-01T00:00:00Z"
            );

    private static final Instant MAX_SUPPORTED_EARNED_AT =
            Instant.parse(
                    "9999-12-31T23:59:59.999999Z"
            );

    private static final Pattern BASE64_URL_PATTERN =
            Pattern.compile(
                    "^[A-Za-z0-9_-]+$"
            );

    /**
     * cursor 값을 외부에 노출할 opaque 문자열로 인코딩합니다.
     */
    public String encode(XpHistoryCursor cursor) {
        Objects.requireNonNull(
                cursor,
                "XP 이력 cursor는 필수입니다."
        );

        validateSupportedEarnedAt(
                cursor.earnedAt()
        );

        String payload =
                String.join(
                        DELIMITER,
                        VERSION,
                        cursor.earnedAt().toString(),
                        cursor.xpHistoryId().toString()
                );

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        payload.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
    }

    /**
     * 외부 cursor 문자열을 keyset 조회값으로 복원합니다.
     *
     * <p>잘못된 cursor 원문은 예외 메시지에 포함하지 않습니다.</p>
     */
    public XpHistoryCursor decode(String encodedCursor) {
        validateEncodedCursor(
                encodedCursor
        );

        byte[] decodedBytes;

        try {
            decodedBytes =
                    Base64.getUrlDecoder()
                            .decode(encodedCursor);
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }

        String canonicalCursor =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(decodedBytes);

        if (!canonicalCursor.equals(encodedCursor)) {
            throw invalidCursor();
        }

        String payload =
                new String(
                        decodedBytes,
                        StandardCharsets.UTF_8
                );

        String[] parts =
                payload.split(
                        SPLIT_DELIMITER,
                        -1
                );

        if (parts.length != PAYLOAD_PART_COUNT
                || !VERSION.equals(parts[0])) {
            throw invalidCursor();
        }

        try {
            Instant earnedAt =
                    Instant.parse(
                            parts[1]
                    );

            UUID xpHistoryId =
                    UUID.fromString(
                            parts[2]
                    );

            validateCanonicalPayload(
                    earnedAt,
                    xpHistoryId,
                    parts[1],
                    parts[2]
            );

            validateSupportedEarnedAt(
                    earnedAt
            );

            return new XpHistoryCursor(
                    earnedAt,
                    xpHistoryId
            );
        } catch (
                DateTimeParseException
                | IllegalArgumentException exception
        ) {
            throw invalidCursor();
        }
    }

    private void validateEncodedCursor(
            String encodedCursor
    ) {
        if (encodedCursor == null
                || encodedCursor.isBlank()
                || encodedCursor.length()
                > XpHistoryCursor.MAX_ENCODED_LENGTH
                || !BASE64_URL_PATTERN
                .matcher(encodedCursor)
                .matches()) {
            throw invalidCursor();
        }
    }

    /**
     * 서버가 생성하는 표준 Instant 및 UUID 표현인지 확인합니다.
     */
    private void validateCanonicalPayload(
            Instant earnedAt,
            UUID xpHistoryId,
            String earnedAtText,
            String xpHistoryIdText
    ) {
        if (!earnedAt.toString().equals(earnedAtText)
                || !xpHistoryId.toString()
                .equals(xpHistoryIdText)) {
            throw invalidCursor();
        }
    }

    /**
     * DB 조회 파라미터로 안전하게 사용할 수 있는 시각인지 확인합니다.
     */
    private void validateSupportedEarnedAt(
            Instant earnedAt
    ) {
        if (earnedAt.isBefore(MIN_SUPPORTED_EARNED_AT)
                || earnedAt.isAfter(MAX_SUPPORTED_EARNED_AT)) {
            throw invalidCursor();
        }
    }

    private BusinessException invalidCursor() {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                "XP 이력 조회 cursor 형식이 올바르지 않습니다."
        );
    }
}
