package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.domain.dailyquiz.ConceptSlots;
import com.maesamco.content.domain.dailyquiz.DailyQuizQuestionTypePolicy;
import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 선정된 개념 슬롯에 목표 문제 유형을 배정
 */
@Component
public class DailyQuizQuestionSlotAllocator {

    public QuestionSlots allocate(ConceptSlots conceptSlots) {
        if (conceptSlots == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "개념 슬롯은 필수입니다.");
        }

        List<DailyQuizProblemType> targetTypes =
                DailyQuizQuestionTypePolicy.targetTypes();

        List<QuestionSlot> questionSlots =
                IntStream.range(0, conceptSlots.size())
                        .mapToObj(index -> new QuestionSlot(conceptSlots.at(index), targetTypes.get(index)))
                        .toList();

        return new QuestionSlots(questionSlots);
    }
}
