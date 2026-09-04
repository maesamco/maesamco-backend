package com.maesamco.coaching.presentation.api_controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 코칭 서비스 API 명세 5번 API(역질문 답변 등록) 요청 바디.
 */
public record FollowUpAnswerRegisterRequest(
        @NotBlank(message = "답변 내용은 필수입니다.")
        @Size(max = 1000, message = "답변은 1000자를 초과할 수 없습니다.")
        String answerText
) {
}
