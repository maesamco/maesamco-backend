package com.maesamco.judge.presentation.request;

import com.maesamco.judge.global.validation.MaxByteSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SubmissionCreateRequest(

        @NotNull(message = "problemId는 필수입니다.")
        UUID problemId,

        @NotBlank(message = "code는 비어 있을 수 없습니다.")
        @MaxByteSize(value = 100_000, message = "code는 최대 100,000바이트를 초과할 수 없습니다.")
        String code,

        String language
) {
}