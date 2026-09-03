package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.HintGenerationFacade;
import com.maesamco.coaching.application.query_service.HintQueryService;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.response.SuccessResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coaching/submissions/{submissionId}/hints")
public class HintApiController {

    private final HintGenerationFacade hintGenerationFacade;
    private final HintQueryService hintQueryService;

    public HintApiController(HintGenerationFacade hintGenerationFacade, HintQueryService hintQueryService) {
        this.hintGenerationFacade = hintGenerationFacade;
        this.hintQueryService = hintQueryService;
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<HintResponse>> requestHint(
            @PathVariable UUID submissionId,
            @AuthenticationPrincipal UUID userId
    ) {
        requireAuthenticated(userId);
        HintGenerationFacade.HintGenerationResult result = hintGenerationFacade.requestHint(submissionId, userId);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(SuccessResponse.success(HintResponse.from(result)));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<List<HintListItemResponse>>> getHints(
            @PathVariable UUID submissionId,
            @AuthenticationPrincipal UUID userId
    ) {
        requireAuthenticated(userId);
        List<HintListItemResponse> hints = hintQueryService.getHints(submissionId, userId).stream()
                .map(HintListItemResponse::from)
                .toList();
        return ResponseEntity.ok(SuccessResponse.success(hints));
    }

    /**
     * SecurityConfig가 anyRequest().permitAll()이고 JwtAuthenticationFilter도 토큰이
     * 없거나 무효하면 SecurityContext를 비운 채 그냥 통과시키므로(각 API가 알아서
     * 막는 구조), 여기서 명시적으로 막지 않으면 userId가 null인 채로 Judge Service
     * 조회까지 진행되어 인증 실패가 SUBMISSION_NOT_FOUND(404) 같은 엉뚱한 응답으로
     * 새어나갈 수 있다(PR #70 리뷰, 용현님 P1).
     */
    private void requireAuthenticated(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}
