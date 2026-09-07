package com.maesamco.judge.presentation.api_controller;

import com.maesamco.judge.application.command_service.SubmissionCommandService;
import com.maesamco.judge.application.command.SubmissionCreateCommand;
import com.maesamco.judge.application.result.SubmissionCreateResult;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.global.response.SuccessResponse;
import com.maesamco.judge.presentation.request.SubmissionCreateRequest;
import com.maesamco.judge.presentation.response.SubmissionCreateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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


    private void requireAuthenticated(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}