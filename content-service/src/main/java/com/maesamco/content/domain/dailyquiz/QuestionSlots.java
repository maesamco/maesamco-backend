package com.maesamco.content.domain.dailyquiz;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;

import java.util.List;
import java.util.Objects;

import static com.maesamco.content.domain.dailyquiz.DailyQuizPolicy.TARGET_QUESTION_COUNT;

/**
 * Daily Quiz 한 세트를 구성하기 위한 유형 포함 문항 슬롯
 */
public record QuestionSlots(List<QuestionSlot> values) {

    public QuestionSlots {
        if (values == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "문항 슬롯 목록은 필수입니다.");
        }
        if (values.size() != TARGET_QUESTION_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "문항 슬롯은 정확히 "
                            + TARGET_QUESTION_COUNT
                            + "개여야 합니다. 실제: "
                            + values.size()
            );
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "문항 슬롯은 비어 있을 수 없습니다.");
        }

        values = List.copyOf(values);
    }

    public QuestionSlot at(int slotIndex) {
        return values.get(slotIndex);
    }

    public int size() {
        return values.size();
    }
}
