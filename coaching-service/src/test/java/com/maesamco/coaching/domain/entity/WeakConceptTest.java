package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeakConceptTest {

    @Test
    @DisplayName("생성 시 발견 횟수는 1, improved는 false이고 lastDetectedAt이 채워진다")
    void create_startsWithSingleOccurrence() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        WeakConcept weakConcept = WeakConcept.create(userId, "재귀");

        // then
        assertThat(weakConcept.getUserId()).isEqualTo(userId);
        assertThat(weakConcept.getConceptTag()).isEqualTo("재귀");
        assertThat(weakConcept.getOccurrenceCount()).isEqualTo(1);
        assertThat(weakConcept.getLastDetectedAt()).isNotNull();
        assertThat(weakConcept.isImproved()).isFalse();
    }

    @Test
    @DisplayName("recordOccurrence()를 호출하면 발견 횟수가 늘고 lastDetectedAt이 갱신된다")
    void recordOccurrence_incrementsCountAndUpdatesTimestamp() {
        // given
        WeakConcept weakConcept = WeakConcept.create(UUID.randomUUID(), "재귀");
        var firstDetectedAt = weakConcept.getLastDetectedAt();

        // when
        weakConcept.recordOccurrence();

        // then
        assertThat(weakConcept.getOccurrenceCount()).isEqualTo(2);
        assertThat(weakConcept.getLastDetectedAt()).isAfterOrEqualTo(firstDetectedAt);
    }

    @Test
    @DisplayName("recordOccurrence(Instant)를 호출하면 발견 횟수가 늘고 lastDetectedAt이 정확히 그 시각으로 갱신된다")
    void recordOccurrence_withExplicitInstant_setsExactTimestamp() {
        // given — isAfterOrEqualTo만으로는 lastDetectedAt 갱신 로직이 실수로 제거돼도 잡아내지
        // 못한다(PR #34 리뷰). 시각을 직접 주입해서 정확한 값으로 결정적으로 검증한다.
        WeakConcept weakConcept = WeakConcept.create(UUID.randomUUID(), "재귀");
        Instant detectedAt = Instant.parse("2026-01-01T00:00:00Z");

        // when
        weakConcept.recordOccurrence(detectedAt);

        // then
        assertThat(weakConcept.getOccurrenceCount()).isEqualTo(2);
        assertThat(weakConcept.getLastDetectedAt()).isEqualTo(detectedAt);
    }

    @Test
    @DisplayName("markImproved()를 호출하면 improved가 true로 바뀐다")
    void markImproved_setsImprovedTrue() {
        // given
        WeakConcept weakConcept = WeakConcept.create(UUID.randomUUID(), "재귀");

        // when
        weakConcept.markImproved();

        // then
        assertThat(weakConcept.isImproved()).isTrue();
    }

    @Test
    @DisplayName("사용자 ID가 null이면 생성할 수 없다")
    void create_throwsWhenUserIdIsNull() {
        assertThatThrownBy(() -> WeakConcept.create(null, "재귀"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("개념 태그가 비어있으면 생성할 수 없다")
    void create_throwsWhenConceptTagIsBlank() {
        assertThatThrownBy(() -> WeakConcept.create(UUID.randomUUID(), " "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("개념 태그가 50자면 생성할 수 있고, 51자면 생성할 수 없다")
    void create_validatesConceptTagMaxLength() {
        String fiftyChars = "a".repeat(50);
        String fiftyOneChars = "a".repeat(51);

        assertThat(WeakConcept.create(UUID.randomUUID(), fiftyChars).getConceptTag())
                .isEqualTo(fiftyChars);

        assertThatThrownBy(() -> WeakConcept.create(UUID.randomUUID(), fiftyOneChars))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("개념 태그의 앞뒤 공백은 저장 전에 제거된다")
    void create_trimsConceptTag() {
        // when
        WeakConcept weakConcept = WeakConcept.create(UUID.randomUUID(), "  재귀  ");

        // then
        assertThat(weakConcept.getConceptTag()).isEqualTo("재귀");
    }
}
