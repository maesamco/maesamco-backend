package com.maesamco.coaching.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFeedbackTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("AI 피드백을 생성하면 필드가 그대로 채워진다")
    void create_setsFields() throws Exception {
        // given
        UUID coachingSessionId = UUID.randomUUID();
        JsonNode understoodConcepts = objectMapper.readTree("[\"반복문\", \"조건문\"]");
        JsonNode explanationGaps = objectMapper.readTree("[\"배열 인덱스 경계값\"]");
        JsonNode weakConcepts = objectMapper.readTree("[\"재귀\"]");

        // when
        AiFeedback aiFeedback = AiFeedback.create(
                coachingSessionId, understoodConcepts, explanationGaps, weakConcepts,
                null, null, null
        );

        // then
        assertThat(aiFeedback.getCoachingSessionId()).isEqualTo(coachingSessionId);
        assertThat(aiFeedback.getUnderstoodConcepts()).isEqualTo(understoodConcepts);
        assertThat(aiFeedback.getExplanationGaps()).isEqualTo(explanationGaps);
        assertThat(aiFeedback.getWeakConcepts()).isEqualTo(weakConcepts);
        assertThat(aiFeedback.getSyntaxToImprove()).isNull();
        assertThat(aiFeedback.getRecommendedProblems()).isNull();
        assertThat(aiFeedback.getNextDirection()).isNull();
    }

    @Test
    @DisplayName("코칭 세션 ID가 null이면 생성할 수 없다")
    void create_throwsWhenCoachingSessionIdIsNull() throws Exception {
        JsonNode empty = objectMapper.readTree("[]");

        assertThatThrownBy(() -> AiFeedback.create(null, empty, empty, empty, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("이해한 개념이 null이면 생성할 수 없다")
    void create_throwsWhenUnderstoodConceptsIsNull() throws Exception {
        JsonNode empty = objectMapper.readTree("[]");

        assertThatThrownBy(() ->
                AiFeedback.create(UUID.randomUUID(), null, empty, empty, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("설명 부족 부분이 null이면 생성할 수 없다")
    void create_throwsWhenExplanationGapsIsNull() throws Exception {
        JsonNode empty = objectMapper.readTree("[]");

        assertThatThrownBy(() ->
                AiFeedback.create(UUID.randomUUID(), empty, null, empty, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("취약 개념이 null이면 생성할 수 없다")
    void create_throwsWhenWeakConceptsIsNull() throws Exception {
        JsonNode empty = objectMapper.readTree("[]");

        assertThatThrownBy(() ->
                AiFeedback.create(UUID.randomUUID(), empty, empty, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
