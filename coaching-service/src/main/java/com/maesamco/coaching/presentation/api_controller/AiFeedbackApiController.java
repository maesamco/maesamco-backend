package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.AiFeedbackRetryFacade;
import com.maesamco.coaching.application.query_service.AiFeedbackQueryService;
import com.maesamco.coaching.domain.entity.AiFeedback;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.response.SuccessResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * AI 종합 피드백 조회/재시도 API(코칭 서비스 API 명세 6번 API, 이슈 #52).
 */
@RestController
@RequestMapping("/api/v1/coaching/submissions/{submissionId}/feedback")
public class AiFeedbackApiController {

    private final AiFeedbackQueryService aiFeedbackQueryService;
    private final AiFeedbackRetryFacade aiFeedbackRetryFacade;

    public AiFeedbackApiController(
            AiFeedbackQueryService aiFeedbackQueryService,
            AiFeedbackRetryFacade aiFeedbackRetryFacade
    ) {
        this.aiFeedbackQueryService = aiFeedbackQueryService;
        this.aiFeedbackRetryFacade = aiFeedbackRetryFacade;
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<AiFeedbackResponse>> getFeedback(
            @PathVariable UUID submissionId,
            @AuthenticationPrincipal UUID userId
    ) {
        requireAuthenticated(userId);
        AiFeedback feedback = aiFeedbackQueryService.getFeedback(submissionId, userId);
        return ResponseEntity.ok(SuccessResponse.success(AiFeedbackResponse.from(feedback)));
    }

    @PostMapping("/retry")
    public ResponseEntity<SuccessResponse<AiFeedbackResponse>> retryFeedback(
            @PathVariable UUID submissionId,
            @AuthenticationPrincipal UUID userId
    ) {
        requireAuthenticated(userId);
        AiFeedback feedback = aiFeedbackRetryFacade.retryFeedback(submissionId, userId);
        return ResponseEntity.ok(SuccessResponse.success(AiFeedbackResponse.from(feedback)));
    }

    /**
     * ExplanationApiController.requireAuthenticated()와 동일한 이유 — SecurityConfig가
     * anyRequest().permitAll()이라 각 API가 알아서 인증을 확인해야 한다(PR #70 리뷰).
     */
    private void requireAuthenticated(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}
