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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    private SubmissionSnapshot submission(UUID subId) {
        return new SubmissionSnapshot(subId, callerId, problemId, "code", "WRONG", List.of(), 1);
    }

    private CoachingSession persistedSession(UUID sessionSubmissionId) {
        CoachingSession session = CoachingSession.create(sessionSubmissionId, callerId, problemId);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    @Test
    void 세션이_없으면_새로_만든다() {
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.empty());
        CoachingSession newSession = persistedSession(submissionId);
        when(coachingSessionRepository.save(any())).thenReturn(newSession);

        CoachingSession result = finder.findOrCreate(submission(submissionId));

        assertThat(result).isSameAs(newSession);
    }

    @Test
    void 세션이_있고_최신_제출과_동일하면_그대로_반환한다() {
        CoachingSession existing = persistedSession(submissionId);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existing));

        CoachingSession result = finder.findOrCreate(submission(submissionId));

        assertThat(result).isSameAs(existing);
        verify(coachingSessionRepository, never()).save(any());
    }

    @Test
    void 세션이_있고_제출이_바뀌었으면_submissionId를_갱신하고_저장한다() {
        UUID newSubmissionId = UUID.randomUUID();
        CoachingSession existing = persistedSession(submissionId);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existing));
        when(coachingSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CoachingSession result = finder.findOrCreate(submission(newSubmissionId));

        assertThat(result.getSubmissionId()).isEqualTo(newSubmissionId);
        verify(coachingSessionRepository).save(existing);
    }

    @Test
    void 동시_생성_레이스로_저장이_실패하면_방금_생성된_세션을_재조회해서_반환한다() {
        CoachingSession racedSession = persistedSession(submissionId);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedSession));
        when(coachingSessionRepository.save(any()))
                .thenThrow(new BusinessException(ErrorCode.COACHING_SESSION_ALREADY_EXISTS));

        CoachingSession result = finder.findOrCreate(submission(submissionId));

        assertThat(result).isSameAs(racedSession);
    }

    @Test
    void 저장_실패가_다른_원인이면_그대로_전파한다() {
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.empty());
        when(coachingSessionRepository.save(any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> finder.findOrCreate(submission(submissionId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
