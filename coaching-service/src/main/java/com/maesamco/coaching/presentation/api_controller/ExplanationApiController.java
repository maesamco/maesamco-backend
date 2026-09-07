package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.ExplanationGenerationFacade;
import com.maesamco.coaching.application.query_service.ExplanationQueryService;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.response.SuccessResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coaching/submissions/{submissionId}/explanations")
public class ExplanationApiController {

    private final ExplanationGenerationFacade explanationGenerationFacade;
    private final ExplanationQueryService explanationQueryService;

    public ExplanationApiController(
            ExplanationGenerationFacade explanationGenerationFacade,
            ExplanationQueryService explanationQueryService
    ) {
        this.explanationGenerationFacade = explanationGenerationFacade;
        this.explanationQueryService = explanationQueryService;
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<ExplanationRegisterResponse>> registerExplanation(
            @PathVariable UUID submissionId,
            @Valid @RequestBody ExplanationRegisterRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        requireAuthenticated(userId);
        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                explanationGenerationFacade.registerExplanation(submissionId, request.content(), userId);
        // 재교차검증 리뷰 대응(ExplanationGenerationFacade.retryExistingExplanation() 참고) —
        // 이미 등록된 설명에 대한 재요청은 created=false로 와서 200, 새로 등록됐으면 201.
        // HintApiController.requestHint()와 동일한 패턴.
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(SuccessResponse.success(ExplanationRegisterResponse.from(result)));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<ExplanationDetailResponse>> getExplanation(
            @PathVariable UUID submissionId,
            @AuthenticationPrincipal UUID userId
    ) {
        requireAuthenticated(userId);
        ExplanationQueryService.ExplanationQueryResult result =
                explanationQueryService.getExplanation(submissionId, userId);
        return ResponseEntity.ok(SuccessResponse.success(ExplanationDetailResponse.from(result)));
    }

    /**
     * HintApiController.requireAuthenticated()와 동일한 이유 — SecurityConfig가
     * anyRequest().permitAll()이라 각 API가 알아서 인증을 확인해야 한다(PR #70 리뷰).
     */
    private void requireAuthenticated(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}
