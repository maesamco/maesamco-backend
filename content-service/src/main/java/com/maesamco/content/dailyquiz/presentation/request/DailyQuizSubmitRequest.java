package com.maesamco.content.dailyquiz.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Daily Quiz 문항 제출 API 요청 DTO
 *
 * 채점 시 사용자가 보낸 값을 기준으로 비교해야 하므로 이 계층에서는
 * 응답 문자열을 정규화하거나 변경하지 않습니다.
 */
public record DailyQuizSubmitRequest(
        @NotBlank(message = "답안은 필수입니다.")
        @Size(max = 200, message = "답안은 200자를 초과할 수 없습니다.")
        String response
) {
}
