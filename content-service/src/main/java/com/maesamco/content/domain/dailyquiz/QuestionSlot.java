package com.maesamco.content.domain.dailyquiz;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;

/**
 * Daily Quiz 문항 하나를 소싱하기 위한 개념 태그와 문제 유형 조합
 */
public record QuestionSlot(
        String conceptTag,
        DailyQuizProblemType problemType
) {

    private static final int MAX_CONCEPT_TAG_LENGTH = 50;

    public QuestionSlot {
        if (conceptTag == null || conceptTag.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "문항 슬롯의 개념 태그는 필수입니다.");
        }
        if (problemType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "문항 슬롯의 문제 유형은 필수입니다.");
        }

        conceptTag = conceptTag.strip();
        if (conceptTag.length() > MAX_CONCEPT_TAG_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "문항 슬롯의 개념 태그는 "
                            + MAX_CONCEPT_TAG_LENGTH
                            + "자를 초과할 수 없습니다."
            );
        }
    }
}
