package com.maesamco.coaching.application.query_service;

import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Hint;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.HintRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HintQueryServiceTest {

    @Mock
    private JudgeServicePort judgeServicePort;
    @Mock
    private CoachingSessionRepository coachingSessionRepository;
    @Mock
    private HintRepository hintRepository;

    private HintQueryService queryService;

    private final UUID submissionId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queryService = new HintQueryService(judgeServicePort, coachingSessionRepository, hintRepository);
    }

    private SubmissionSnapshot submission(UUID id, UUID owner) {
        return new SubmissionSnapshot(id, owner, problemId, "code", "WRONG", List.of(), 1);
    }

    private CoachingSession persistedSession(UUID sessionSubmissionId) {
        CoachingSession session = CoachingSession.create(sessionSubmissionId, callerId, problemId);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    @Test
    void 본인_소유가_아닌_제출이면_SUBMISSION_NOT_FOUND() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(submission(submissionId, UUID.randomUUID()));

        assertThatThrownBy(() -> queryService.getHints(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND);
    }

    @Test
    void 요청한_submissionId가_세션의_최신_제출과_일치하면_힌트_목록을_반환한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(submission(submissionId, callerId));
        CoachingSession session = persistedSession(submissionId);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(session));
        Hint stage1 = Hint.create(session.getId(), 1, "1단계");
        when(hintRepository.findByCoachingSessionId(session.getId())).thenReturn(List.of(stage1));

        List<Hint> hints = queryService.getHints(submissionId, callerId);

        assertThat(hints).containsExactly(stage1);
    }

    /**
     * PR #70 리뷰(용현님 P2) — V4 시절엔 이미 COMPLETED된 회차의 옛 submissionId로 조회하면
     * 그 사이 새로 시작된 다른 회차(세션)의 힌트가 반환되던 문제였다. V5(이슈 #84)로 세션이
     * 문제당 유일해진 뒤에도, 같은 세션 안에서 더 예전 제출 ID로 조회하면 여전히 이 가드가
     * 막아준다.
     */
    @Test
    void 요청한_submissionId가_세션의_최신_제출과_다르면_빈_목록을_반환한다() {
        UUID staleSubmissionId = UUID.randomUUID();
        when(judgeServicePort.getSubmission(staleSubmissionId)).thenReturn(submission(staleSubmissionId, callerId));
        // 세션의 최신 제출은 submissionId — staleSubmissionId는 그보다 이전에 있었던 제출
        CoachingSession session = persistedSession(submissionId);
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(session));

        List<Hint> hints = queryService.getHints(staleSubmissionId, callerId);

        assertThat(hints).isEmpty();
    }

    @Test
    void 진행중인_세션이_없으면_빈_목록을_반환한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(submission(submissionId, callerId));
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.empty());

        List<Hint> hints = queryService.getHints(submissionId, callerId);

        assertThat(hints).isEmpty();
    }
}
