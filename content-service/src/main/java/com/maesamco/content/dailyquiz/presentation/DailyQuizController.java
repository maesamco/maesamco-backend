package com.maesamco.content.dailyquiz.presentation;

import com.maesamco.content.dailyquiz.application.query.DailyQuizGetQuery;
import com.maesamco.content.dailyquiz.application.query_service.DailyQuizGetQueryService;
import com.maesamco.content.dailyquiz.application.result.DailyQuizGetResult;
import com.maesamco.content.dailyquiz.presentation.response.DailyQuizGetResponse;
import com.maesamco.content.global.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/daily-quiz")
public class DailyQuizController {

    private final DailyQuizGetQueryService queryService;

    /**
     * 인증된 사용자의 오늘 Daily Quiz 세트를 조회하고 최초 진입이면 시작 처리합니다.
     * 오늘의 Daily Quiz 세트와 문항별 진행 상태
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
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
}
