package com.maesamco.coaching.global.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;

/**
 * 도메인 엔티티 생성자에서 반복되는 필수값 검증을 모아둔 유틸리티.
 *
 * coaching-service 도메인 구현이 마무리되며(CoachingSession/Hint/Explanation/
 * FollowUpQuestion/FollowUpAnswer/AiFeedback/AiCallHistory 7개 엔티티에 거의 동일한
 * requireNonNull/requireText가 중복돼 있었음) 추출했다.
 */
public final class Validate {

    private Validate() {
    }

    public static <T> T requireNonNull(T value, String fieldNameKorean) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + particle(fieldNameKorean) + " 필수입니다.");
        }
        return value;
    }

    /**
     * JsonNode 전용 오버로드 — Java 참조가 null이 아니어도 JSON의 null을 나타내는
     * NullNode(예: ObjectMapper.readTree("null"))가 들어오면 이 역시 "필수값 없음"으로
     * 취급한다(PR #8 리뷰). 위의 제네릭 requireNonNull은 value == null만 확인해서
     * NullNode를 통과시켜 버린다.
     */
    public static JsonNode requireNonNull(JsonNode value, String fieldNameKorean) {
        if (value == null || value.isNull()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + particle(fieldNameKorean) + " 필수입니다.");
        }
        return value;
    }

    public static String requireText(String value, String fieldNameKorean) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + particle(fieldNameKorean) + " 필수입니다.");
        }
        return value;
    }

    /**
     * null/blank 검증에 더해 DB 컬럼 길이(maxLength)까지 함께 검증한다 — 길이 제약이 있는
     * 컬럼은 도메인 생성 시점에 걸러내지 않으면 DB 저장 시점에야 실패한다(PR #8 리뷰).
     */
    public static String requireText(String value, int maxLength, String fieldNameKorean) {
        String checked = requireText(value, fieldNameKorean);
        return requireMaxLength(checked, maxLength, fieldNameKorean);
    }

    /**
     * null은 허용하되(nullable 컬럼), 값이 있으면 DB 컬럼 길이(maxLength)를 검증한다.
     */
    public static String requireMaxLengthIfPresent(String value, int maxLength, String fieldNameKorean) {
        if (value == null) {
            return null;
        }
        return requireMaxLength(value, maxLength, fieldNameKorean);
    }

    private static String requireMaxLength(String value, int maxLength, String fieldNameKorean) {
        if (value.length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    fieldNameKorean + particle(fieldNameKorean) + " " + maxLength + "자를 초과할 수 없습니다.");
        }
        return value;
    }

    /**
     * null은 허용하되(실패한 호출 등 값이 없을 수 있는 컬럼), 값이 있으면 0 이상인지 검증한다.
     */
    public static Integer requireNonNegativeIfPresent(Integer value, String fieldNameKorean) {
        if (value != null && value < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + particle(fieldNameKorean) + " 0 이상이어야 합니다.");
        }
        return value;
    }

    /**
     * NOT NULL 컬럼(retryCount 등 primitive int 필드)이 0 이상인지 검증한다. 위
     * requireNonNegativeIfPresent(Integer, ...)는 nullable 컬럼용이라 값이 항상 있는
     * primitive int에는 맞지 않는다.
     */
    public static int requireNonNegative(int value, String fieldNameKorean) {
        if (value < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + particle(fieldNameKorean) + " 0 이상이어야 합니다.");
        }
        return value;
    }

    /**
     * "은/는" 조사를 마지막 글자의 받침 여부로 판단한다. 기존에는 필드 타입에 따라 "는"/"은"을
     * 하드코딩해서, 받침 있는 이름에 "는"이 붙는 오류가 있었다(예: "AI 호출 목적는 필수입니다").
     */
    private static String particle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return "는";
        }
        int finalConsonant = (last - 0xAC00) % 28;
        return finalConsonant == 0 ? "는" : "은";
    }
}
