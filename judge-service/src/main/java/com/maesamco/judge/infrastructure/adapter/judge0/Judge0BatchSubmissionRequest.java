package com.maesamco.judge.infrastructure.adapter.judge0;

import java.util.List;

public record Judge0BatchSubmissionRequest(
        List<Judge0SubmissionRequest> submissions
) {
    public static Judge0BatchSubmissionRequest of(List<Judge0SubmissionRequest> items) {
        return new Judge0BatchSubmissionRequest(items);
    }
}