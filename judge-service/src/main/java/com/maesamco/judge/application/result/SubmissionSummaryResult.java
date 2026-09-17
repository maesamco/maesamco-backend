package com.maesamco.judge.application.result;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import java.time.Instant;
import java.util.UUID;

public record SubmissionSummaryResult(
        UUID submissionId,
        UUID problemId,
        int attemptNo,
        SubmissionStatus status,
        SubmissionResult result,
        Instant submittedAt
) {
    public static SubmissionSummaryResult from(Submission submission) {
        return new SubmissionSummaryResult(
                submission.getId(),
                submission.getProblemId(),
                submission.getAttemptNo(),
                submission.getStatus(),
                submission.getResult(),
                submission.getSubmittedAt()
        );
    }
}