package com.maesamco.judge.presentation.internal_controller;

import com.maesamco.judge.application.query.SubmissionGetQuery;
import com.maesamco.judge.application.query_service.SubmissionQueryService;
import com.maesamco.judge.global.response.SuccessResponse;
import com.maesamco.judge.presentation.response.SubmissionInternalGetResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/submissions")
@RequiredArgsConstructor
public class SubmissionInternalController {

    private final SubmissionQueryService submissionQueryService;

    @GetMapping("/{submissionId}")
    public SuccessResponse<SubmissionInternalGetResponse> getSubmission(@PathVariable UUID submissionId) {
        SubmissionInternalGetResponse response =
                submissionQueryService.getSubmissionForInternal(SubmissionGetQuery.from(submissionId));
        return SuccessResponse.success(response);
    }
}