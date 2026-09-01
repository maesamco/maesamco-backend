package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCallHistoryTest {

    @Test
    @DisplayName("AI 호출 이력을 생성하면 필드가 그대로 채워진다")
    void create_setsFields() {
        // given
        UUID coachingSessionId = UUID.randomUUID();

        // when
        AiCallHistory aiCallHistory = AiCallHistory.create(
                coachingSessionId, AiCallPurpose.HINT, "gpt-4o", "v1",
                "SUCCESS", 1200, 350, null, 0
        );

        // then
        assertThat(aiCallHistory.getCoachingSessionId()).isEqualTo(coachingSessionId);
        assertThat(aiCallHistory.getPurpose()).isEqualTo(AiCallPurpose.HINT);
        assertThat(aiCallHistory.getModelName()).isEqualTo("gpt-4o");
        assertThat(aiCallHistory.getPromptVersion()).isEqualTo("v1");
        assertThat(aiCallHistory.getRequestStatus()).isEqualTo("SUCCESS");
        assertThat(aiCallHistory.getResponseTimeMs()).isEqualTo(1200);
        assertThat(aiCallHistory.getTokenUsage()).isEqualTo(350);
        assertThat(aiCallHistory.getFailureReason()).isNull();
        assertThat(aiCallHistory.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("실패한 호출은 응답 시간·토큰 사용량 없이 실패 원인만 채워서 생성할 수 있다")
    void create_allowsFailureWithoutResponseMetrics() {
        // when
        AiCallHistory aiCallHistory = AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.FEEDBACK, "gpt-4o", "v1",
                "FAILED", null, null, "timeout", 1
        );

        // then
        assertThat(aiCallHistory.getResponseTimeMs()).isNull();
        assertThat(aiCallHistory.getTokenUsage()).isNull();
        assertThat(aiCallHistory.getFailureReason()).isEqualTo("timeout");
        assertThat(aiCallHistory.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("코칭 세션 ID가 null이면 생성할 수 없다")
    void create_throwsWhenCoachingSessionIdIsNull() {
        assertThatThrownBy(() -> AiCallHistory.create(
                null, AiCallPurpose.HINT, "gpt-4o", "v1", "SUCCESS", null, null, null, 0
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("AI 호출 목적이 null이면 생성할 수 없다")
    void create_throwsWhenPurposeIsNull() {
        assertThatThrownBy(() -> AiCallHistory.create(
                UUID.randomUUID(), null, "gpt-4o", "v1", "SUCCESS", null, null, null, 0
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("모델명이 비어있으면 생성할 수 없다")
    void create_throwsWhenModelNameIsBlank() {
        assertThatThrownBy(() -> AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, " ", "v1", "SUCCESS", null, null, null, 0
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("재시도 횟수가 음수이면 생성할 수 없다")
    void create_throwsWhenRetryCountIsNegative() {
        assertThatThrownBy(() -> AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, "gpt-4o", "v1", "SUCCESS", null, null, null, -1
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("모델명이 50자면 생성할 수 있고, 51자면 생성할 수 없다")
    void create_validatesModelNameMaxLength() {
        String fiftyChars = "a".repeat(50);
        String fiftyOneChars = "a".repeat(51);

        assertThat(AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, fiftyChars, "v1", "SUCCESS", null, null, null, 0
        ).getModelName()).isEqualTo(fiftyChars);

        assertThatThrownBy(() -> AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, fiftyOneChars, "v1", "SUCCESS", null, null, null, 0
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("프롬프트 버전이 20자면 생성할 수 있고, 21자면 생성할 수 없다")
    void create_validatesPromptVersionMaxLength() {
        String twentyChars = "v".repeat(20);
        String twentyOneChars = "v".repeat(21);

        assertThat(AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, "gpt-4o", twentyChars, "SUCCESS", null, null, null, 0
        ).getPromptVersion()).isEqualTo(twentyChars);

        assertThatThrownBy(() -> AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, "gpt-4o", twentyOneChars, "SUCCESS", null, null, null, 0
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("요청 상태가 20자면 생성할 수 있고, 21자면 생성할 수 없다")
    void create_validatesRequestStatusMaxLength() {
        String twentyChars = "s".repeat(20);
        String twentyOneChars = "s".repeat(21);

        assertThat(AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, "gpt-4o", "v1", twentyChars, null, null, null, 0
        ).getRequestStatus()).isEqualTo(twentyChars);

        assertThatThrownBy(() -> AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, "gpt-4o", "v1", twentyOneChars, null, null, null, 0
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("응답 시간·토큰 사용량은 null이거나 0 이상이면 생성할 수 있고, 음수면 생성할 수 없다")
    void create_validatesResponseTimeMsAndTokenUsageAreNonNegative() {
        assertThat(AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, "gpt-4o", "v1", "SUCCESS", 0, 0, null, 0
        ).getResponseTimeMs()).isEqualTo(0);

        assertThatThrownBy(() -> AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, "gpt-4o", "v1", "SUCCESS", -1, null, null, 0
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> AiCallHistory.create(
                UUID.randomUUID(), AiCallPurpose.HINT, "gpt-4o", "v1", "SUCCESS", null, -1, null, 0
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
