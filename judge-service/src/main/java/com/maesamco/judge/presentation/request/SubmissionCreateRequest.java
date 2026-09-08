package com.maesamco.judge.presentation.request;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.global.validation.MaxByteSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmissionCreateRequest(

        @NotNull(message = "problemId는 필수입니다.")
        UUID problemId,

        @NotBlank(message = "code는 비어 있을 수 없습니다.")
        @MaxByteSize(value = Submission.MAX_CODE_BYTES, message = "code는 최대 100KB(UTF-8 기준)를 초과할 수 없습니다.")
        String code,

        String language
) {
}