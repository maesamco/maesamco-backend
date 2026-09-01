package com.maesamco.coaching.global.util;

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

    public static String requireText(String value, String fieldNameKorean) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldNameKorean + particle(fieldNameKorean) + " 필수입니다.");
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
