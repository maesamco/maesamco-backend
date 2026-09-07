package com.maesamco.coaching.application.facade;

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
                feedbackGenerationFacade
        );
    }

    private CoachingSession completedSession(UUID owner) {
        CoachingSession session = CoachingSession.create(submissionId, owner, problemId, 1);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(session, "completedAt", completedAt);
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
        when(aiCallHistoryRepository.countByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
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
        when(aiCallHistoryRepository.countByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
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
        when(aiCallHistoryRepository.countByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
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
        when(aiCallHistoryRepository.countByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
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
}
