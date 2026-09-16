package com.maesamco.judge.presentation.api_controller;

import com.maesamco.judge.application.command_service.SubmissionCommandService;
import com.maesamco.judge.application.command.SubmissionCreateCommand;
import com.maesamco.judge.application.query_service.SubmissionQueryService;
import com.maesamco.judge.application.result.SubmissionCreateResult;
import com.maesamco.judge.application.result.SubmissionExternalGetResult;
import com.maesamco.judge.application.result.SubmissionSummaryResult;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.global.response.PageResponse;
import com.maesamco.judge.global.response.SuccessResponse;
import com.maesamco.judge.global.util.PageableFactory;
import com.maesamco.judge.presentation.request.SubmissionCreateRequest;
import com.maesamco.judge.presentation.response.SubmissionCreateResponse;
import com.maesamco.judge.presentation.response.SubmissionExternalGetResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionApiController implements SubmissionApiDocs {

    private final SubmissionCommandService submissionCommandService;
    private final SubmissionQueryService submissionQueryService;

    @Override
    @PostMapping
    public ResponseEntity<SuccessResponse<SubmissionCreateResponse>> create(
            @AuthenticationPrincipal UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmissionCreateRequest request
    ) {
        requireAuthenticated(userId);
        SubmissionCreateCommand command = SubmissionCreateCommand.from(userId, idempotencyKey, request);
        SubmissionCreateResult result = submissionCommandService.submit(command);

        SubmissionCreateResponse response = SubmissionCreateResponse.of(result.submissionId(), result.status());
        HttpStatus status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;

        return ResponseEntity
                .status(status)
                .body(SuccessResponse.success(response));
    }

    @Override
    @GetMapping("/{submissionId}")
    public ResponseEntity<SuccessResponse<SubmissionExternalGetResponse>> getSubmission(
            @PathVariable UUID submissionId,
            @AuthenticationPrincipal UUID userId
    ) {
        requireAuthenticated(userId);
        SubmissionExternalGetResult result = submissionQueryService.getSubmission(submissionId, userId);
        SubmissionExternalGetResponse response = SubmissionExternalGetResponse.from(result);
        return ResponseEntity.status(HttpStatus.OK).body(SuccessResponse.success(response));
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<PageResponse<SubmissionSummaryResult>>> getMySubmissions (
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) UUID problemId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        requireAuthenticated(userId);
        Pageable pageable = PageableFactory.of(page, size, sort, sort);
        PageResponse<SubmissionSummaryResult> result =
                submissionQueryService.getSubmissions(userId, problemId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(SuccessResponse.success(result));
    }


    private void requireAuthenticated(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}