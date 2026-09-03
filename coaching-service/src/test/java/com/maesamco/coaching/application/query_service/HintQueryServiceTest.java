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
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(session));
        Hint stage1 = Hint.create(session.getId(), 1, "1단계");
        when(hintRepository.findByCoachingSessionId(session.getId())).thenReturn(List.of(stage1));

        List<Hint> hints = queryService.getHints(submissionId, callerId);

        assertThat(hints).containsExactly(stage1);
    }

    /**
     * PR #70 리뷰(용현님 P2) — 이미 COMPLETED된 회차의 옛 submissionId로 조회하면
     * 그 사이 새로 시작된 다른 회차(IN_PROGRESS 세션)의 힌트가 반환되던 문제.
     */
    @Test
    void 요청한_submissionId가_세션의_최신_제출과_다르면_빈_목록을_반환한다() {
        UUID staleSubmissionId = UUID.randomUUID();
        when(judgeServicePort.getSubmission(staleSubmissionId)).thenReturn(submission(staleSubmissionId, callerId));
        // 세션은 그 사이 새로 시작된 다른 회차(최신 제출 = submissionId, 옛 제출인 staleSubmissionId와는 다름)
        CoachingSession session = persistedSession(submissionId);
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(session));

        List<Hint> hints = queryService.getHints(staleSubmissionId, callerId);

        assertThat(hints).isEmpty();
    }

    @Test
    void 진행중인_세션이_없으면_빈_목록을_반환한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(submission(submissionId, callerId));
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.empty());

        List<Hint> hints = queryService.getHints(submissionId, callerId);

        assertThat(hints).isEmpty();
    }
}
