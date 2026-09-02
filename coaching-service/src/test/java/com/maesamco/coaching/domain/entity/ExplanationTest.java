package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExplanationTest {

    @Test
    @DisplayName("설명을 생성하면 필드가 그대로 채워진다")
    void create_setsFields() {
        // given
        UUID coachingSessionId = UUID.randomUUID();
        String content = "이 문제는 배열 인덱스가 0부터 시작한다는 점이 핵심입니다.";

        // when
        Explanation explanation = Explanation.create(coachingSessionId, content);

        // then
        assertThat(explanation.getCoachingSessionId()).isEqualTo(coachingSessionId);
        assertThat(explanation.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("코칭 세션 ID가 null이면 생성할 수 없다")
    void create_throwsWhenCoachingSessionIdIsNull() {
        assertThatThrownBy(() -> Explanation.create(null, "내용"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("본문이 비어있으면 생성할 수 없다")
    void create_throwsWhenContentIsBlank() {
        assertThatThrownBy(() -> Explanation.create(UUID.randomUUID(), "   "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("본문이 null이면 생성할 수 없다")
    void create_throwsWhenContentIsNull() {
        assertThatThrownBy(() -> Explanation.create(UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
