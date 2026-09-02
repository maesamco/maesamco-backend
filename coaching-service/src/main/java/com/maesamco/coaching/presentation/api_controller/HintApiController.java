package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.HintGenerationFacade;
import com.maesamco.coaching.application.query_service.HintQueryService;
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
        HintGenerationFacade.HintGenerationResult result = hintGenerationFacade.requestHint(submissionId, userId);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(SuccessResponse.success(HintResponse.from(result)));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<List<HintListItemResponse>>> getHints(
            @PathVariable UUID submissionId,
            @AuthenticationPrincipal UUID userId
    ) {
        List<HintListItemResponse> hints = hintQueryService.getHints(submissionId, userId).stream()
                .map(HintListItemResponse::from)
                .toList();
        return ResponseEntity.ok(SuccessResponse.success(hints));
    }
}
