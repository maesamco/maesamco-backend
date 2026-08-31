package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HintTest {

    @Test
    @DisplayName("힌트를 생성하면 필드가 그대로 채워진다")
    void create_setsFields() {
        // given
        UUID coachingSessionId = UUID.randomUUID();
        int stage = 1;
        String content = "먼저 반복문 조건을 다시 확인해보세요.";

        // when
        Hint hint = Hint.create(coachingSessionId, stage, content);

        // then
        assertThat(hint.getCoachingSessionId()).isEqualTo(coachingSessionId);
        assertThat(hint.getStage()).isEqualTo(stage);
        assertThat(hint.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("코칭 세션 ID가 null이면 생성할 수 없다")
    void create_throwsWhenCoachingSessionIdIsNull() {
        assertThatThrownBy(() -> Hint.create(null, 1, "내용"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5})
    @DisplayName("단계가 1~4 범위를 벗어나면 생성할 수 없다")
    void create_throwsWhenStageOutOfRange(int invalidStage) {
        assertThatThrownBy(() -> Hint.create(UUID.randomUUID(), invalidStage, "내용"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("본문이 비어있으면 생성할 수 없다")
    void create_throwsWhenContentIsBlank() {
        assertThatThrownBy(() -> Hint.create(UUID.randomUUID(), 1, "   "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
