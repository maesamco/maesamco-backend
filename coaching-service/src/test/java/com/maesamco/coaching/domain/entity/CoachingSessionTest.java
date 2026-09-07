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
        CoachingSession coachingSession = CoachingSession.create(submissionId, userId, problemId, 1);

        // then
        assertThat(coachingSession.getSubmissionId()).isEqualTo(submissionId);
        assertThat(coachingSession.getUserId()).isEqualTo(userId);
        assertThat(coachingSession.getProblemId()).isEqualTo(problemId);
        assertThat(coachingSession.getLastAttemptNo()).isEqualTo(1);
        assertThat(coachingSession.getStatus()).isEqualTo(CoachingSessionStatus.IN_PROGRESS);
        assertThat(coachingSession.getCompletedAt()).isNull();
        assertThat(coachingSession.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("complete()를 호출하면 상태가 COMPLETED로 바뀌고 completedAt이 채워진다")
    void complete_marksSessionCompleted() {
        // given
        CoachingSession coachingSession = CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);

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
        assertThatThrownBy(() -> CoachingSession.create(null, UUID.randomUUID(), UUID.randomUUID(), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("이미 완료된 세션을 다시 complete()하면 예외가 발생한다")
    void complete_throwsWhenAlreadyCompleted() {
        // given
        CoachingSession coachingSession = CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);
        coachingSession.complete();

        // when & then
        assertThatThrownBy(coachingSession::complete)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.COACHING_SESSION_ALREADY_COMPLETED);
    }

    /**
     * PR #88 리뷰(용현님 P1) 대응 — advanceToSubmission()의 핵심 방어 로직 자체를 검증한다.
     */
    @Test
    @DisplayName("attemptNo가 더 크면 submissionId와 lastAttemptNo를 갈아타고 true를 반환한다")
    void advanceToSubmission_updatesWhenAttemptNoIsGreater() {
        // given
        CoachingSession coachingSession = CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);
        UUID newSubmissionId = UUID.randomUUID();

        // when
        boolean advanced = coachingSession.advanceToSubmission(newSubmissionId, 2);

        // then
        assertThat(advanced).isTrue();
        assertThat(coachingSession.getSubmissionId()).isEqualTo(newSubmissionId);
        assertThat(coachingSession.getLastAttemptNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("attemptNo가 더 작거나 같으면 아무 것도 바꾸지 않고 false를 반환한다")
    void advanceToSubmission_ignoresWhenAttemptNoIsNotGreater() {
        // given
        UUID originalSubmissionId = UUID.randomUUID();
        CoachingSession coachingSession = CoachingSession.create(originalSubmissionId, UUID.randomUUID(), UUID.randomUUID(), 3);

        // when — 같은 attemptNo(멱등 재호출)와 더 오래된 attemptNo(과거 제출에 대한
        // 지연된 요청) 둘 다 세션을 역행시키면 안 된다.
        boolean advancedWithSameAttempt = coachingSession.advanceToSubmission(UUID.randomUUID(), 3);
        boolean advancedWithOlderAttempt = coachingSession.advanceToSubmission(UUID.randomUUID(), 2);

        // then
        assertThat(advancedWithSameAttempt).isFalse();
        assertThat(advancedWithOlderAttempt).isFalse();
        assertThat(coachingSession.getSubmissionId()).isEqualTo(originalSubmissionId);
        assertThat(coachingSession.getLastAttemptNo()).isEqualTo(3);
    }
}
