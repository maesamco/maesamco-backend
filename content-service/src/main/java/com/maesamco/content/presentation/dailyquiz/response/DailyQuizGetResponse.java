package com.maesamco.content.presentation.dailyquiz.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.maesamco.content.application.dailyquiz.result.DailyQuizGetResult;
import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionGetResult;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttemptStatus;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 오늘의 Daily Quiz 조회 API 응답 DTO입니다.
 */
@Schema(description = "오늘의 일일 퀴즈 세트와 문항별 진행 상태")
public record DailyQuizGetResponse(
        // 오늘의 Daily Quiz 세트 ID
        @Schema(description = "오늘 생성된 퀴즈 세트 ID")
        UUID quizAttemptId,
        // 조회 및 최초 시작 처리가 반영된 세트 상태
        @Schema(description = "첫 조회에서 READY가 IN_PROGRESS로 전환된 후의 상태")
        DailyQuizAttemptStatus attemptStatus,
        // 세트에 실제로 배정된 전체 문항 수
        @Schema(description = "실제 배정 문항 수. 정상 5개, 일부 생성 실패 시 3~4개", example = "5")
        int totalCount,
        // 클라이언트가 적용할 타이머 표시 정책
        @Schema(description = "타이머 표시 정책. 일일 퀴즈는 QUICK_ANSWER 고정", example = "QUICK_ANSWER")
        String timerPolicy,
        // 세트 풀이 권장 시간(초). 제출 제한 시간은 아님
        @Schema(description = "권장 풀이 시간(초). 초과해도 제출 가능", example = "180")
        int recommendedDurationSeconds,
        // 사용자가 세트를 최초로 시작한 시각
        @Schema(description = "첫 조회 시 기록된 시작 시각")
        Instant startedAt,
        // 문항 순서대로 정렬된 배정 문항 응답 목록
        @Schema(description = "questionOrder 오름차순의 배정 문항")
        List<QuestionResponse> questions
) {
    /**
     * Application 계층의 세트 조회 결과를 API 응답 DTO로 변환
     */
    public static DailyQuizGetResponse from(DailyQuizGetResult result) {
        List<QuestionResponse> list = result.questions().stream()
                .map(QuestionResponse::from)
                .toList();

        return new DailyQuizGetResponse(
                result.quizAttemptId(),
                result.attemptStatus(),
                result.totalCount(),
                result.timerPolicy(),
                result.recommendedDurationSeconds(),
                result.startedAt(),
                list
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "배정 문항과 사용자의 제출 상태")
    public record QuestionResponse(
            @Schema(description = "버전이 바뀌어도 유지되는 논리적 문항 ID")
            UUID questionGroupId,
            @Schema(description = "제출과 신고에 사용하는 특정 문항 버전 ID")
            UUID questionVersionId,
            @Schema(description = "문항 버전 번호")
            int versionNo,
            @Schema(description = "세트 내 문항 순서")
            int questionOrder,
            @Schema(description = "객관식·단답형·빈칸형 중 문항 유형")
            DailyQuizProblemType problemType,
            @Schema(description = "문제 지문")
            String prompt,
            @Schema(description = "객관식 문항에서만 제공되는 선택지")
            List<String> options,
            @Schema(description = "문항 제출 여부")
            boolean answered,
            @Schema(description = "제출한 문항에서만 제공되는 정답 여부")
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
