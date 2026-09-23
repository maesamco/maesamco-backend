package com.maesamco.content.presentation.dailyquiz;

import com.maesamco.content.application.dailyquiz.command.DailyQuizSubmitCommand;
import com.maesamco.content.application.dailyquiz.query.DailyQuizGetQuery;
import com.maesamco.content.application.dailyquiz.query_service.DailyQuizGetQueryService;
import com.maesamco.content.application.dailyquiz.result.DailyQuizGetResult;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSubmitResult;
import com.maesamco.content.application.dailyquiz.service.DailyQuizSubmitService;
import com.maesamco.content.presentation.dailyquiz.request.DailyQuizSubmitRequest;
import com.maesamco.content.presentation.dailyquiz.response.DailyQuizGetResponse;
import com.maesamco.content.presentation.dailyquiz.response.DailyQuizSubmitResponse;
import com.maesamco.content.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/daily-quiz")
@Tag(name = "Daily Quiz", description = "일일 퀴즈 조회 및 문항 제출 API")
@SecurityRequirement(name = "bearerAuth")
public class DailyQuizController implements DailyQuizApiDocs {

    private final DailyQuizGetQueryService queryService;
    private final DailyQuizSubmitService submitService;

    /**
     * 인증된 사용자의 오늘 Daily Quiz 세트를 조회하고 최초 진입이면 시작 처리합니다.
     * 오늘의 Daily Quiz 세트와 문항별 진행 상태
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Override
    public SuccessResponse<DailyQuizGetResponse> getDailyQuiz(
            @AuthenticationPrincipal UUID userId
    ) {
        // 인증된 사용자 ID를 Application 계층의 조회 조건으로 변환합니다.
        DailyQuizGetQuery query = DailyQuizGetQuery.from(userId);

        // 조회와 최초 시작 처리는 Application Service에서 수행합니다.
        DailyQuizGetResult result = queryService.get(query);

        // Application 결과를 API 응답 DTO와 공통 성공 응답 형식으로 변환합니다.
        return SuccessResponse.success(DailyQuizGetResponse.from(result));
    }

    /**
     * Daily Quiz에 배정된 문항 하나를 제출하고 즉시 채점합니다.
     */
    @PostMapping("/{quizAttemptId}/questions/{questionVersionId}/submit")
    @PreAuthorize("isAuthenticated()")
    @Override
    public SuccessResponse<DailyQuizSubmitResponse> submitQuestion(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID quizAttemptId,
            @PathVariable UUID questionVersionId,
            @Valid @RequestBody DailyQuizSubmitRequest request
    ) {
        DailyQuizSubmitCommand command = DailyQuizSubmitCommand.from(
                userId,
                quizAttemptId,
                questionVersionId,
                request.response()
        );

        DailyQuizSubmitResult result = submitService.submit(command);

        return SuccessResponse.success(DailyQuizSubmitResponse.from(result));
    }
}
