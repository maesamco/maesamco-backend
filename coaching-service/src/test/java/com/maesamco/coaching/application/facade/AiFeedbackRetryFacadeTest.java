package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.port.AiFeedbackRetryLockPort;
import com.maesamco.coaching.domain.entity.AiCallPurpose;
import com.maesamco.coaching.domain.entity.AiFeedback;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.AiFeedbackRepository;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.ExplanationRepository;
import com.maesamco.coaching.domain.repository.FollowUpAnswerRepository;
import com.maesamco.coaching.domain.repository.FollowUpQuestionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiFeedbackRetryFacadeTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private CoachingSessionRepository coachingSessionRepository;
    @Mock
    private AiFeedbackRepository aiFeedbackRepository;
    @Mock
    private AiCallHistoryRepository aiCallHistoryRepository;
    @Mock
    private ExplanationRepository explanationRepository;
    @Mock
    private FollowUpQuestionRepository followUpQuestionRepository;
    @Mock
    private FollowUpAnswerRepository followUpAnswerRepository;
    @Mock
    private FeedbackGenerationFacade feedbackGenerationFacade;
    @Mock
    private AiFeedbackRetryLockPort aiFeedbackRetryLockPort;

    private AiFeedbackRetryFacade retryFacade;

    private final UUID submissionId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();

    private final Instant completedAt = Instant.parse("2026-09-07T12:00:00Z");

    @BeforeEach
    void setUp() {
        retryFacade = new AiFeedbackRetryFacade(
                coachingSessionRepository,
                aiFeedbackRepository,
                aiCallHistoryRepository,
                explanationRepository,
                followUpQuestionRepository,
                followUpAnswerRepository,
                feedbackGenerationFacade,
                aiFeedbackRetryLockPort
        );
        // 세션이 없거나 소유권이 안 맞는 극초반 실패 테스트는 락 획득 단계까지 안 가서
        // 이 스텁을 안 쓴다 — lenient()로 strict-stub 검증에서 제외한다.
        lenient().when(aiFeedbackRetryLockPort.tryLock(any(), any())).thenReturn(true);
    }

    private CoachingSession completedSession(UUID owner) {
        CoachingSession session = CoachingSession.create(submissionId, owner, problemId, 1);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        // complete()로 status까지 COMPLETED로 만든 뒤, completedAt만 테스트가 원하는
        // 고정 시각으로 덮어쓴다 — complete()는 completedAt을 Instant.now()로 정하기
        // 때문에 테스트에서 Explanation과의 상대 시간(예: completedAt.minusSeconds(5))을
        // 계산하려면 값을 직접 통제해야 한다.
        session.complete();
        ReflectionTestUtils.setField(session, "completedAt", completedAt);
        return session;
    }

    /** isCompleted() 검증용 — status가 IN_PROGRESS인 채로 남는 미완료 세션. */
    private CoachingSession inProgressSession(UUID owner) {
        CoachingSession session = CoachingSession.create(submissionId, owner, problemId, 1);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    private Explanation explanationCreatedAt(UUID coachingSessionId, Instant createdAt) {
        Explanation explanation = Explanation.create(coachingSessionId, submissionId, "설명 내용");
        ReflectionTestUtils.setField(explanation, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(explanation, "createdAt", createdAt);
        return explanation;
    }

    private FollowUpQuestion followUpQuestion(UUID explanationId) {
        FollowUpQuestion question = FollowUpQuestion.create(explanationId, "질문", "경계값");
        ReflectionTestUtils.setField(question, "id", UUID.randomUUID());
        return question;
    }

    private FollowUpAnswer followUpAnswer(UUID followUpQuestionId) {
        FollowUpAnswer answer = FollowUpAnswer.create(followUpQuestionId, "답변");
        ReflectionTestUtils.setField(answer, "id", UUID.randomUUID());
        return answer;
    }

    private AiFeedback feedback(UUID coachingSessionId) {
        ArrayNode emptyArray = JSON_MAPPER.createArrayNode();
        return AiFeedback.create(coachingSessionId, emptyArray, emptyArray, emptyArray, null, null, "다음 방향");
    }

    @Test
    void 세션이_없으면_SUBMISSION_NOT_FOUND() {
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> retryFacade.retryFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND);
    }

    @Test
    void 본인_소유가_아닌_세션이면_SUBMISSION_NOT_FOUND() {
        CoachingSession session = completedSession(UUID.randomUUID());
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> retryFacade.retryFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND);
    }

    @Test
    void 이미_피드백이_존재하면_AI_FEEDBACK_ALREADY_EXISTS() {
        CoachingSession session = completedSession(callerId);
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRepository.findByCoachingSessionId(session.getId()))
                .thenReturn(Optional.of(feedback(session.getId())));

        assertThatThrownBy(() -> retryFacade.retryFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_FEEDBACK_ALREADY_EXISTS);

        verify(feedbackGenerationFacade, never())
                .generateFeedback(any(), any(), any(), any());
    }

    @Test
    void 재시도_횟수를_초과하면_AI_FEEDBACK_RETRY_LIMIT_EXCEEDED() {
        CoachingSession session = completedSession(callerId);
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRepository.findByCoachingSessionId(session.getId())).thenReturn(Optional.empty());
        when(aiCallHistoryRepository.countRealAttemptsByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
                .thenReturn(4L);

        assertThatThrownBy(() -> retryFacade.retryFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_FEEDBACK_RETRY_LIMIT_EXCEEDED);

        verify(feedbackGenerationFacade, never())
                .generateFeedback(any(), any(), any(), any());
    }

    @Test
    void 재시도에_성공하면_새로_생성된_피드백을_반환한다() {
        CoachingSession session = completedSession(callerId);

        Explanation explanation = explanationCreatedAt(session.getId(), completedAt);
        FollowUpQuestion question = followUpQuestion(explanation.getId());
        FollowUpAnswer answer = followUpAnswer(question.getId());
        AiFeedback createdFeedback = feedback(session.getId());

        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRepository.findByCoachingSessionId(session.getId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(createdFeedback));
        when(aiCallHistoryRepository.countRealAttemptsByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
                .thenReturn(1L);
        when(explanationRepository.findByCoachingSessionId(session.getId()))
                .thenReturn(List.of(explanation));
        when(followUpQuestionRepository.findByExplanationId(explanation.getId()))
                .thenReturn(Optional.of(question));
        when(followUpAnswerRepository.findByFollowUpQuestionId(question.getId()))
                .thenReturn(Optional.of(answer));

        AiFeedback result = retryFacade.retryFeedback(submissionId, callerId);

        assertThat(result).isSameAs(createdFeedback);
        verify(feedbackGenerationFacade)
                .generateFeedback(session, explanation, question, answer);
    }

    @Test
    void 재시도해도_여전히_실패하면_AI_FEEDBACK_NOT_FOUND() {
        CoachingSession session = completedSession(callerId);

        Explanation explanation = explanationCreatedAt(session.getId(), completedAt);
        FollowUpQuestion question = followUpQuestion(explanation.getId());
        FollowUpAnswer answer = followUpAnswer(question.getId());

        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRepository.findByCoachingSessionId(session.getId())).thenReturn(Optional.empty());
        when(aiCallHistoryRepository.countRealAttemptsByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
                .thenReturn(1L);
        when(explanationRepository.findByCoachingSessionId(session.getId()))
                .thenReturn(List.of(explanation));
        when(followUpQuestionRepository.findByExplanationId(explanation.getId()))
                .thenReturn(Optional.of(question));
        when(followUpAnswerRepository.findByFollowUpQuestionId(question.getId()))
                .thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> retryFacade.retryFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_FEEDBACK_NOT_FOUND);
    }

    @Test
    void 완료_시점에_가장_가까운_설명을_재구성_대상으로_고른다() {
        CoachingSession session = completedSession(callerId);

        Explanation farExplanation = explanationCreatedAt(session.getId(), completedAt.minusSeconds(3600));
        Explanation closeExplanation = explanationCreatedAt(session.getId(), completedAt.minusSeconds(5));
        FollowUpQuestion question = followUpQuestion(closeExplanation.getId());
        FollowUpAnswer answer = followUpAnswer(question.getId());
        AiFeedback createdFeedback = feedback(session.getId());

        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRepository.findByCoachingSessionId(session.getId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(createdFeedback));
        when(aiCallHistoryRepository.countRealAttemptsByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
                .thenReturn(1L);
        when(explanationRepository.findByCoachingSessionId(session.getId()))
                .thenReturn(List.of(farExplanation, closeExplanation));
        when(followUpQuestionRepository.findByExplanationId(closeExplanation.getId()))
                .thenReturn(Optional.of(question));
        when(followUpAnswerRepository.findByFollowUpQuestionId(question.getId()))
                .thenReturn(Optional.of(answer));

        retryFacade.retryFeedback(submissionId, callerId);

        verify(feedbackGenerationFacade)
                .generateFeedback(session, closeExplanation, question, answer);
        verify(followUpQuestionRepository, never())
                .findByExplanationId(farExplanation.getId());
    }

    /**
     * 자가 리뷰(P1) — 세션이 아직 완료 전(completedAt=null)이면 findCompletionExplanation()의
     * Duration.between()에서 NPE가 나던 케이스. 후보가 정확히 1개면 Stream.min()이
     * 컴파레이터를 아예 호출하지 않아 NPE가 안 나므로, 반드시 2개 이상으로 재현해야 한다.
     * 지금은 isCompleted() 체크가 그 전에 막아서 이 경로 자체를 안 타야 한다 — NPE 대신
     * AI_FEEDBACK_NOT_FOUND(404)로 응답하는지 확인한다.
     */
    @Test
    void 세션이_아직_완료되지_않았으면_설명이_여러_개여도_NPE_대신_AI_FEEDBACK_NOT_FOUND() {
        CoachingSession session = inProgressSession(callerId);
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> retryFacade.retryFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_FEEDBACK_NOT_FOUND);

        verify(explanationRepository, never()).findByCoachingSessionId(any());
        verify(feedbackGenerationFacade, never()).generateFeedback(any(), any(), any(), any());
    }

    /**
     * 용현님 리뷰(P1) — .abs() 비교자가 완료 시점 이후에 생긴(재도전) Explanation을
     * "더 가깝다"는 이유로 잘못 고르던 케이스. 완료 30초 전 Explanation과 완료 5초 후
     * Explanation이 있으면, 절대값 비교로는 후자가 더 가깝지만 완료를 만들어낸 답변일 리
     * 없으므로 반드시 전자가 선택돼야 한다.
     */
    @Test
    void 완료_시점_이후에_생긴_설명은_더_가까워도_후보에서_제외한다() {
        CoachingSession session = completedSession(callerId);

        Explanation beforeCompletion = explanationCreatedAt(session.getId(), completedAt.minusSeconds(30));
        Explanation afterCompletion = explanationCreatedAt(session.getId(), completedAt.plusSeconds(5));
        FollowUpQuestion question = followUpQuestion(beforeCompletion.getId());
        FollowUpAnswer answer = followUpAnswer(question.getId());
        AiFeedback createdFeedback = feedback(session.getId());

        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRepository.findByCoachingSessionId(session.getId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(createdFeedback));
        when(aiCallHistoryRepository.countRealAttemptsByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
                .thenReturn(1L);
        when(explanationRepository.findByCoachingSessionId(session.getId()))
                .thenReturn(List.of(beforeCompletion, afterCompletion));
        when(followUpQuestionRepository.findByExplanationId(beforeCompletion.getId()))
                .thenReturn(Optional.of(question));
        when(followUpAnswerRepository.findByFollowUpQuestionId(question.getId()))
                .thenReturn(Optional.of(answer));

        retryFacade.retryFeedback(submissionId, callerId);

        verify(feedbackGenerationFacade)
                .generateFeedback(session, beforeCompletion, question, answer);
        verify(followUpQuestionRepository, never())
                .findByExplanationId(afterCompletion.getId());
    }

    /**
     * 재검증(PR #111, 외부 AI 리뷰) — 같은 세션에 대한 동시 재시도 요청이 락을 못 얻으면
     * LLM을 호출하지 않고 즉시 AI_FEEDBACK_RETRY_IN_PROGRESS(409)로 응답해야 한다.
     */
    @Test
    void 락을_못_얻으면_LLM을_호출하지_않고_AI_FEEDBACK_RETRY_IN_PROGRESS() {
        CoachingSession session = completedSession(callerId);
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRetryLockPort.tryLock(eq(session.getId()), any())).thenReturn(false);

        assertThatThrownBy(() -> retryFacade.retryFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_FEEDBACK_RETRY_IN_PROGRESS);

        verify(aiFeedbackRepository, never()).findByCoachingSessionId(any());
        verify(feedbackGenerationFacade, never()).generateFeedback(any(), any(), any(), any());
        verify(aiFeedbackRetryLockPort, never()).unlock(any(), any());
    }

    /**
     * 락을 정상적으로 획득해서 처리한 뒤에는(성공이든 실패든) 반드시 해제해야 한다 —
     * finally 블록으로 보장하는지 확인.
     */
    @Test
    void 처리가_끝나면_락을_해제한다() {
        CoachingSession session = completedSession(callerId);
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRepository.findByCoachingSessionId(session.getId()))
                .thenReturn(Optional.of(feedback(session.getId())));

        assertThatThrownBy(() -> retryFacade.retryFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class);

        verify(aiFeedbackRetryLockPort).tryLock(eq(session.getId()), any());
        verify(aiFeedbackRetryLockPort).unlock(eq(session.getId()), any());
    }
}
