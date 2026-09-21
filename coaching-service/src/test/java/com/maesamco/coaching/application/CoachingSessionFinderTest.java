package com.maesamco.coaching.application;

import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이슈 #84로 HintGenerationFacade에서 추출된 find-or-create 로직 자체를 검증한다 —
 * HintGenerationFacadeTest는 이 클래스를 실제 객체로 감싸 쓰므로, 여기서 검증한 동작은
 * 그쪽에서도 그대로 재사용된다.
 */
@ExtendWith(MockitoExtension.class)
class CoachingSessionFinderTest {

    @Mock
    private CoachingSessionRepository coachingSessionRepository;

    private CoachingSessionFinder finder;

    private final UUID callerId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        finder = new CoachingSessionFinder(coachingSessionRepository);
    }

    private SubmissionSnapshot submission(UUID subId, int attemptNo) {
        return new SubmissionSnapshot(subId, callerId, problemId, UUID.randomUUID(),"code", "WRONG", List.of(), attemptNo);
    }

    private CoachingSession persistedSession(UUID sessionSubmissionId, int attemptNo) {
        CoachingSession session = CoachingSession.create(sessionSubmissionId, callerId, problemId, attemptNo);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    @Test
    void 세션이_없으면_새로_만든다() {
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.empty());
        CoachingSession newSession = persistedSession(submissionId, 1);
        when(coachingSessionRepository.save(any())).thenReturn(newSession);

        CoachingSession result = finder.findOrCreate(submission(submissionId, 1));

        assertThat(result).isSameAs(newSession);
    }

    @Test
    void 세션이_있고_최신_제출과_동일하면_그대로_반환한다() {
        CoachingSession existing = persistedSession(submissionId, 1);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existing));

        CoachingSession result = finder.findOrCreate(submission(submissionId, 1));

        assertThat(result).isSameAs(existing);
        verify(coachingSessionRepository, never()).save(any());
    }

    @Test
    void 세션이_있고_더_최신_제출이_들어오면_submissionId를_갱신하고_저장한다() {
        UUID newSubmissionId = UUID.randomUUID();
        CoachingSession existing = persistedSession(submissionId, 1);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existing));
        when(coachingSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CoachingSession result = finder.findOrCreate(submission(newSubmissionId, 2));

        assertThat(result.getSubmissionId()).isEqualTo(newSubmissionId);
        verify(coachingSessionRepository).save(existing);
    }

    /**
     * PR #88 리뷰(용현님 P1) — Hint/Explanation이 이 로직을 공유하면서, 과거 제출(지연된
     * 요청, 이미 지난 정답 제출에 대한 뒤늦은 설명 등록 등)에 대한 요청이 세션의
     * submissionId를 최신 제출 이전으로 되돌릴 수 있었다. attemptNo가 더 작은 제출이
     * 들어오면 세션을 건드리지 않아야 한다 — 그래야 HintQueryService가 최신 제출 기준으로
     * 힌트를 조회할 때 엉뚱하게 빈 목록을 받는 회귀가 재발하지 않는다.
     */
    @Test
    void 세션이_있고_더_오래된_제출이_들어오면_submissionId를_역행시키지_않는다() {
        UUID latestSubmissionId = UUID.randomUUID();
        CoachingSession existing = persistedSession(latestSubmissionId, 3);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existing));

        UUID olderSubmissionId = UUID.randomUUID();
        CoachingSession result = finder.findOrCreate(submission(olderSubmissionId, 2));

        assertThat(result.getSubmissionId()).isEqualTo(latestSubmissionId);
        assertThat(result.getLastAttemptNo()).isEqualTo(3);
        verify(coachingSessionRepository, never()).save(any());
    }

    @Test
    void 동시_생성_레이스로_저장이_실패하면_방금_생성된_세션을_재조회해서_반환한다() {
        CoachingSession racedSession = persistedSession(submissionId, 1);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedSession));
        when(coachingSessionRepository.save(any()))
                .thenThrow(new BusinessException(ErrorCode.COACHING_SESSION_ALREADY_EXISTS));

        CoachingSession result = finder.findOrCreate(submission(submissionId, 1));

        assertThat(result).isSameAs(racedSession);
    }

    /**
     * PR #228 리뷰(용현님 P1) — 위 테스트는 경합에서 진 요청과 먼저 생성된 세션이 같은
     * attemptNo(둘 다 1)라 advanceToSubmission()이 애초에 아무것도 안 바꾸는 케이스였다.
     * 실제 문제는 attemptNo=1과 attemptNo=2가 동시에 최초 생성을 시도해서 attempt=1이
     * 이기고 attempt=2가 경합에서 졌을 때다 — 재조회한 세션(attempt=1)을 그대로 반환하면
     * attempt=2가 최신 제출이라는 사실 자체가 유실된다. 재조회한 세션도 advanceAndSave()에
     * 태워서 최신 attempt로 갱신되는지 검증한다.
     */
    @Test
    void 동시_생성_레이스에서_진_요청이_더_최신_attempt이면_재조회한_세션에도_advanceToSubmission을_적용한다() {
        CoachingSession racedSession = persistedSession(submissionId, 1);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedSession));
        when(coachingSessionRepository.save(any()))
                .thenThrow(new BusinessException(ErrorCode.COACHING_SESSION_ALREADY_EXISTS))
                .thenAnswer(inv -> inv.getArgument(0));

        UUID newerSubmissionId = UUID.randomUUID();
        CoachingSession result = finder.findOrCreate(submission(newerSubmissionId, 2));

        assertThat(result).isSameAs(racedSession);
        assertThat(result.getSubmissionId()).isEqualTo(newerSubmissionId);
        assertThat(result.getLastAttemptNo()).isEqualTo(2);
        verify(coachingSessionRepository, times(2)).save(any());
    }

    @Test
    void 저장_실패가_다른_원인이면_그대로_전파한다() {
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.empty());
        when(coachingSessionRepository.save(any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> finder.findOrCreate(submission(submissionId, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    /**
     * 이슈 #218(V15) — CoachingSession에 @Version 도입 후, 동시에 서로 다른 제출로
     * submissionId를 갈아태우려는 두 요청 중 나중에 flush되는 쪽의 save()가
     * ObjectOptimisticLockingFailureException을 받을 수 있다. 재조회 후
     * advanceToSubmission()을 다시 적용해서 한 번 재시도하면 성공해야 한다.
     */
    @Test
    void 세션_갱신_중_낙관적_락_충돌이_나면_재조회해서_한_번_재시도한다() {
        UUID newSubmissionId = UUID.randomUUID();
        CoachingSession staleSession = persistedSession(submissionId, 1);
        CoachingSession refetchedSession = persistedSession(submissionId, 1);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId))
                .thenReturn(Optional.of(staleSession))
                .thenReturn(Optional.of(refetchedSession));
        when(coachingSessionRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException("충돌"))
                .thenAnswer(inv -> inv.getArgument(0));

        CoachingSession result = finder.findOrCreate(submission(newSubmissionId, 2));

        assertThat(result).isSameAs(refetchedSession);
        assertThat(result.getSubmissionId()).isEqualTo(newSubmissionId);
        verify(coachingSessionRepository, times(2)).findByUserIdAndProblemId(callerId, problemId);
        verify(coachingSessionRepository, times(2)).save(any());
    }

    @Test
    void 재시도한_저장까지_낙관적_락_충돌이_나면_COACHING_SESSION_UPDATE_CONFLICT로_변환한다() {
        UUID newSubmissionId = UUID.randomUUID();
        CoachingSession staleSession = persistedSession(submissionId, 1);
        CoachingSession refetchedSession = persistedSession(submissionId, 1);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId))
                .thenReturn(Optional.of(staleSession))
                .thenReturn(Optional.of(refetchedSession));
        when(coachingSessionRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException("충돌"));

        assertThatThrownBy(() -> finder.findOrCreate(submission(newSubmissionId, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COACHING_SESSION_UPDATE_CONFLICT);

        verify(coachingSessionRepository, times(2)).save(any());
    }
}
