package com.maesamco.content.dailyquiz.domain;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;

import java.util.List;

import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.TARGET_QUESTION_COUNT;

/**
 * Daily Quiz 한 세트를 구성하기 위해 필요한 개념 슬롯입니다.
 * 슬롯의 입력 순서와 중복은 유지합니다.
 */
public record ConceptSlots(List<String> values) {

    private static final int MAX_CONCEPT_LENGTH = 50;

    public ConceptSlots {
        if (values == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "개념 슬롯 목록은 필수입니다."
            );
        }

        if (values.size() != TARGET_QUESTION_COUNT) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "개념 슬롯은 정확히 " + TARGET_QUESTION_COUNT + "개여야 합니다. 실제: " + values.size()
            );
        }

        values = values.stream()
                .map(ConceptSlots::normalize)
                .toList();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "개념 슬롯 값은 비어 있을 수 없습니다."
            );
        }

        String normalized = value.strip();
        if (normalized.length() > MAX_CONCEPT_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "개념 슬롯 값은 " + MAX_CONCEPT_LENGTH + "자를 초과할 수 없습니다."
            );
        }

        return normalized;
    }

    public String at(int slotIndex) {
        return values.get(slotIndex);
    }

    public int size() {
        return values.size();
    }
}
