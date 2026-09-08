package com.maesamco.content.dailyquiz.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.maesamco.content.dailyquiz.application.result.DailyQuizGetResult;
import com.maesamco.content.dailyquiz.application.result.DailyQuizQuestionGetResult;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptStatus;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizProblemType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 오늘의 Daily Quiz 조회 API 응답 DTO입니다.
 */
public record DailyQuizGetResponse(
        // 오늘의 Daily Quiz 세트 ID
        UUID quizAttemptId,
        // 조회 및 최초 시작 처리가 반영된 세트 상태
        DailyQuizAttemptStatus attemptStatus,
        // 세트에 실제로 배정된 전체 문항 수
        int totalCount,
        // 클라이언트가 적용할 타이머 표시 정책
        String timerPolicy,
        // 세트 풀이 권장 시간(초). 제출 제한 시간은 아님
        int recommendedDurationSeconds,
        // 사용자가 세트를 최초로 시작한 시각
        Instant startedAt,
        // 문항 순서대로 정렬된 배정 문항 응답 목록
        List<QuestionResponse> questions
) {

    private static final String TIMER_POLICY = "QUICK_ANSWER";
    private static final int RECOMMENDED_DURATION_SECONDS = 180;

    /**
     * 세트 조회 결과에 Daily Quiz의 고정 타이머 정책을 결합하여
     * API 응답 DTO로 변환
     */
    public static DailyQuizGetResponse from(DailyQuizGetResult result) {
        List<QuestionResponse> list = result.questions().stream()
                .map(QuestionResponse::from)
                .toList();

        return new DailyQuizGetResponse(
                result.quizAttemptId(),
                result.attemptStatus(),
                result.totalCount(),
                TIMER_POLICY,
                RECOMMENDED_DURATION_SECONDS,
                result.startedAt(),
                list
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionResponse(
            UUID questionGroupId,
            UUID questionVersionId,
            int versionNo,
            int questionOrder,
            DailyQuizProblemType problemType,
            String prompt,
            List<String> options,
            boolean answered,
            Boolean correct
    ) {

        /**
         * result 배정 문항 정보와 사용자의 제출 상태를 담은 조회 결과
         * 클라이언트에 반환할 개별 문항 응답
         */
        public static QuestionResponse from(DailyQuizQuestionGetResult result) {
            List<String> options = result.options() == null
                    ? null
                    : List.copyOf(result.options());

            return new QuestionResponse(
                    result.questionGroupId(),
                    result.questionVersionId(),
                    result.versionNo(),
                    result.questionOrder(),
                    result.problemType(),
                    result.prompt(),
                    options,
                    result.answered(),
                    result.correct()
            );
        }
    }
}
