package com.maesamco.coaching.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
        JsonNode understoodTags = objectMapper.readTree("[\"반복문\", \"조건문\"]");
        JsonNode explanationGaps = objectMapper.readTree("[\"배열 인덱스 경계값\"]");
        JsonNode weakTags = objectMapper.readTree("[\"재귀\"]");

        // when
        AiFeedback aiFeedback = AiFeedback.create(
                coachingSessionId, understoodTags, explanationGaps, weakTags,
                null, null, null
        );

        // then
        assertThat(aiFeedback.getCoachingSessionId()).isEqualTo(coachingSessionId);
        assertThat(aiFeedback.getUnderstoodTags()).isEqualTo(understoodTags);
        assertThat(aiFeedback.getExplanationGaps()).isEqualTo(explanationGaps);
        assertThat(aiFeedback.getWeakTags()).isEqualTo(weakTags);
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
    @DisplayName("이해한 태그가 null이면 생성할 수 없다")
    void create_throwsWhenUnderstoodTagsIsNull() throws Exception {
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
    @DisplayName("취약 태그가 null이면 생성할 수 없다")
    void create_throwsWhenWeakTagsIsNull() throws Exception {
        JsonNode empty = objectMapper.readTree("[]");

        assertThatThrownBy(() ->
                AiFeedback.create(UUID.randomUUID(), empty, empty, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("필수 JSONB 필드가 Java null이 아니라 JSON의 null(NullNode)이어도 생성할 수 없다")
    void create_throwsWhenRequiredFieldsAreJsonNull() throws Exception {
        JsonNode empty = objectMapper.readTree("[]");
        JsonNode jsonNull = objectMapper.readTree("null");

        assertThatThrownBy(() ->
                AiFeedback.create(UUID.randomUUID(), jsonNull, empty, empty, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() ->
                AiFeedback.create(UUID.randomUUID(), empty, jsonNull, empty, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() ->
                AiFeedback.create(UUID.randomUUID(), empty, empty, jsonNull, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("생성 후 원본 JsonNode를 수정해도 엔티티 내부 상태는 바뀌지 않는다")
    void create_isNotAffectedByMutatingOriginalNodeAfterConstruction() throws Exception {
        // given
        ArrayNode understoodTags = (ArrayNode) objectMapper.readTree("[\"반복문\"]");
        JsonNode empty = objectMapper.readTree("[]");

        AiFeedback aiFeedback = AiFeedback.create(
                UUID.randomUUID(), understoodTags, empty, empty, null, null, null
        );

        // when — 생성에 사용한 원본 노드를 나중에 수정
        understoodTags.add("조건문");

        // then
        assertThat(aiFeedback.getUnderstoodTags()).isEqualTo(objectMapper.readTree("[\"반복문\"]"));
    }

    @Test
    @DisplayName("getter로 반환받은 JsonNode를 수정해도 엔티티 내부 상태는 바뀌지 않는다")
    void getter_returnsDefensiveCopy() throws Exception {
        // given
        JsonNode empty = objectMapper.readTree("[]");
        AiFeedback aiFeedback = AiFeedback.create(
                UUID.randomUUID(), objectMapper.readTree("[\"반복문\"]"), empty, empty, null, null, null
        );

        // when — getter로 받은 참조를 수정
        ((ArrayNode) aiFeedback.getUnderstoodTags()).add("조건문");

        // then
        assertThat(aiFeedback.getUnderstoodTags()).isEqualTo(objectMapper.readTree("[\"반복문\"]"));
    }
}
