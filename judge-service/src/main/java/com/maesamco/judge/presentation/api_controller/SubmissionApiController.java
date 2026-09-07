package com.maesamco.judge.presentation.api_controller;

import com.maesamco.judge.application.SubmissionService;
import com.maesamco.judge.application.command.SubmissionCreateCommand;
import com.maesamco.judge.application.result.SubmissionCreateResult;
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

    private final SubmissionService submissionService;

    @Override
    @PostMapping
    public ResponseEntity<SuccessResponse<SubmissionCreateResponse>> create(
            @AuthenticationPrincipal UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmissionCreateRequest request
    ) {
        SubmissionCreateCommand command = SubmissionCreateCommand.from(userId, idempotencyKey, request);
        SubmissionCreateResult result = submissionService.submit(command);

        SubmissionCreateResponse response = SubmissionCreateResponse.of(result.submissionId(), result.status());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(SuccessResponse.success(response));
    }
}