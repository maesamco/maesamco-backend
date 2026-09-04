package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.persistence_service.FollowUpAnswerPersistenceService;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.CoachingSessionStatus;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.ExplanationRepository;
import com.maesamco.coaching.domain.repository.FollowUpQuestionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpAnswerFacadeTest {

    @Mock
    private FollowUpQuestionRepository followUpQuestionRepository;
    @Mock
    private ExplanationRepository explanationRepository;
    @Mock
    private CoachingSessionRepository coachingSessionRepository;
    @Mock
    private FollowUpAnswerPersistenceService followUpAnswerPersistenceService;
    @Mock
    private FeedbackGenerationFacade feedbackGenerationFacade;

    private FollowUpAnswerFacade facade;

    private final UUID followUpQuestionId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        facade = new FollowUpAnswerFacade(
                followUpQuestionRepository, explanationRepository, coachingSessionRepository,
                followUpAnswerPersistenceService, feedbackGenerationFacade
        );
    }

    private FollowUpQuestion followUpQuestion(UUID explanationId) {
        FollowUpQuestion question = FollowUpQuestion.create(explanationId, "질문 내용", null);
        ReflectionTestUtils.setField(question, "id", followUpQuestionId);
        return question;
    }

    private Explanation explanation(UUID coachingSessionId) {
        Explanation explanation = Explanation.create(coachingSessionId, submissionId, "설명 내용");
        ReflectionTestUtils.setField(explanation, "id", UUID.randomUUID());
        return explanation;
    }

    private CoachingSession session(UUID owner) {
        CoachingSession session = CoachingSession.create(submissionId, owner, problemId, 1);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    @Test
    void 존재하지_않는_역질문이면_404() {
        when(followUpQuestionRepository.findById(followUpQuestionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.registerAnswer(followUpQuestionId, "답변", callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_UP_QUESTION_NOT_FOUND);

        verify(followUpAnswerPersistenceService, never()).completeWithAnswer(any(), any(), any());
        verify(feedbackGenerationFacade, never()).generateFeedback(any(), any(), any(), any());
    }

    @Test
    void 본인_소유가_아니면_역질문_존재_여부를_숨기고_404() {
        CoachingSession othersSession = session(UUID.randomUUID());
        Explanation explanation = explanation(othersSession.getId());
        FollowUpQuestion question = followUpQuestion(explanation.getId());
        when(followUpQuestionRepository.findById(followUpQuestionId)).thenReturn(Optional.of(question));
        when(explanationRepository.findById(explanation.getId())).thenReturn(Optional.of(explanation));
        when(coachingSessionRepository.findById(othersSession.getId())).thenReturn(Optional.of(othersSession));

        assertThatThrownBy(() -> facade.registerAnswer(followUpQuestionId, "답변", callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_UP_QUESTION_NOT_FOUND);

        verify(followUpAnswerPersistenceService, never()).completeWithAnswer(any(), any(), any());
    }

    @Test
    void 정상_등록되면_피드백_생성까지_호출하고_완료된_세션_상태를_반환한다() {
        CoachingSession ownedSession = session(callerId);
        Explanation explanation = explanation(ownedSession.getId());
        FollowUpQuestion question = followUpQuestion(explanation.getId());
        when(followUpQuestionRepository.findById(followUpQuestionId)).thenReturn(Optional.of(question));
        when(explanationRepository.findById(explanation.getId())).thenReturn(Optional.of(explanation));
        when(coachingSessionRepository.findById(ownedSession.getId())).thenReturn(Optional.of(ownedSession));

        FollowUpAnswer answer = FollowUpAnswer.create(followUpQuestionId, "답변");
        ReflectionTestUtils.setField(answer, "id", UUID.randomUUID());
        ownedSession.complete();
        when(followUpAnswerPersistenceService.completeWithAnswer(ownedSession.getId(), followUpQuestionId, "답변"))
                .thenReturn(new FollowUpAnswerPersistenceService.FollowUpAnswerCompletionResult(answer, ownedSession));

        FollowUpAnswerFacade.FollowUpAnswerRegisterResult result = facade.registerAnswer(followUpQuestionId, "답변", callerId);

        assertThat(result.followUpAnswer()).isSameAs(answer);
        assertThat(result.coachingSessionStatus()).isEqualTo(CoachingSessionStatus.COMPLETED);
        verify(feedbackGenerationFacade).generateFeedback(ownedSession, explanation, question, answer);
    }

    /**
     * FeedbackGenerationFacade는 자기 안에서도 예외를 삼키지만(이중 방어), 여기서도 한 번 더
     * try/catch로 감싸 호출자 실수로 예외가 새더라도 답변 등록 응답 자체는 영향받지 않게 한다.
     */
    @Test
    void 피드백_생성이_예외를_던져도_답변_등록_결과는_그대로_반환한다() {
        CoachingSession ownedSession = session(callerId);
        Explanation explanation = explanation(ownedSession.getId());
        FollowUpQuestion question = followUpQuestion(explanation.getId());
        when(followUpQuestionRepository.findById(followUpQuestionId)).thenReturn(Optional.of(question));
        when(explanationRepository.findById(explanation.getId())).thenReturn(Optional.of(explanation));
        when(coachingSessionRepository.findById(ownedSession.getId())).thenReturn(Optional.of(ownedSession));

        FollowUpAnswer answer = FollowUpAnswer.create(followUpQuestionId, "답변");
        ReflectionTestUtils.setField(answer, "id", UUID.randomUUID());
        ownedSession.complete();
        when(followUpAnswerPersistenceService.completeWithAnswer(ownedSession.getId(), followUpQuestionId, "답변"))
                .thenReturn(new FollowUpAnswerPersistenceService.FollowUpAnswerCompletionResult(answer, ownedSession));
        org.mockito.Mockito.doThrow(new RuntimeException("AI 호출 실패"))
                .when(feedbackGenerationFacade).generateFeedback(any(), any(), any(), any());

        FollowUpAnswerFacade.FollowUpAnswerRegisterResult result =
                facade.registerAnswer(followUpQuestionId, "답변", callerId);

        assertThat(result.followUpAnswer()).isSameAs(answer);
        assertThat(result.coachingSessionStatus()).isEqualTo(CoachingSessionStatus.COMPLETED);
    }
}
