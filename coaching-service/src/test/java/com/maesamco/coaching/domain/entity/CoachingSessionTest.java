package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoachingSessionTest {

    @Test
    @DisplayName("생성 시 상태는 IN_PROGRESS이고 completedAt은 비어있다")
    void create_startsInProgress() {
        // given
        UUID submissionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        // when
        CoachingSession coachingSession = CoachingSession.create(submissionId, userId, problemId);

        // then
        assertThat(coachingSession.getSubmissionId()).isEqualTo(submissionId);
        assertThat(coachingSession.getUserId()).isEqualTo(userId);
        assertThat(coachingSession.getProblemId()).isEqualTo(problemId);
        assertThat(coachingSession.getStatus()).isEqualTo(CoachingSessionStatus.IN_PROGRESS);
        assertThat(coachingSession.getCompletedAt()).isNull();
        assertThat(coachingSession.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("complete()를 호출하면 상태가 COMPLETED로 바뀌고 completedAt이 채워진다")
    void complete_marksSessionCompleted() {
        // given
        CoachingSession coachingSession = CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        // when
        coachingSession.complete();

        // then
        assertThat(coachingSession.getStatus()).isEqualTo(CoachingSessionStatus.COMPLETED);
        assertThat(coachingSession.getCompletedAt()).isNotNull();
        assertThat(coachingSession.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("필수 식별자가 null이면 생성할 수 없다")
    void create_throwsWhenRequiredIdIsNull() {
        // when & then
        assertThatThrownBy(() -> CoachingSession.create(null, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("이미 완료된 세션을 다시 complete()하면 예외가 발생한다")
    void complete_throwsWhenAlreadyCompleted() {
        // given
        CoachingSession coachingSession = CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        coachingSession.complete();

        // when & then
        assertThatThrownBy(coachingSession::complete)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.COACHING_SESSION_ALREADY_COMPLETED);
    }
}
