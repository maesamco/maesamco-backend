package com.maesamco.content.dailyquiz.application.result;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizProblemType;

import java.util.List;
import java.util.UUID;

/**
 * 오늘의 Daily Quiz에 배정된 문항 한 개의 조회 결과 DTO입니다.
 */
public record DailyQuizQuestionGetResult(
        // 논리적으로 같은 문제를 식별하는 그룹 ID
        UUID questionGroupId,
        // 사용자에게 실제 배정된 특정 문제 버전 ID
        UUID questionVersionId,
        // 배정된 문제의 버전 번호
        int versionNo,
        // 세트 안에서 문항이 노출되는 순서
        int questionOrder,
        // 문항 유형
        DailyQuizProblemType problemType,
        // 사용자에게 노출할 문제 지문
        String prompt,
        // 객관식 선택지. 객관식이 아니면 null
        List<String> options,
        // 사용자가 해당 문항을 제출했는지 여부
        boolean answered,
        // 제출한 답안의 정답 여부. 미제출 문항이면 null
        Boolean correct
) {
}
