package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.CoachingSessionFinder;
import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
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
class ExplanationGenerationFacadeTest {

    @Mock
    private JudgeServicePort judgeServicePort;
    @Mock
    private CoachingSessionRepository coachingSessionRepository;
    @Mock
    private ExplanationRepository explanationRepository;
    @Mock
    private FollowUpQuestionRepository followUpQuestionRepository;
    @Mock
    private AiModelPort aiModelPort;
    @Mock
    private AiCallHistoryRepository aiCallHistoryRepository;

    private ExplanationGenerationFacade facade;

    private final UUID submissionId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // HintGenerationFacadeTest와 동일한 이유 — CoachingSessionFinder 자체 동작은
        // CoachingSessionFinderTest에서 따로 검증하고, 여기서는 Facade가 그 결과를
        // 올바르게 받아 쓰는지만 본다.
        facade = new ExplanationGenerationFacade(
                judgeServicePort, new CoachingSessionFinder(coachingSessionRepository),
                explanationRepository, followUpQuestionRepository, aiModelPort, aiCallHistoryRepository
        );
    }

    private SubmissionSnapshot correctSubmission(UUID owner) {
        return new SubmissionSnapshot(submissionId, owner, problemId, "public class Main {}", "CORRECT", List.of(), 1);
    }

    /**
     * HintGenerationFacadeTest.persistedSession()과 동일한 이유 — mock Repository로는
     * Hibernate의 saveAndFlush 시점 ID 생성이 일어나지 않으므로 직접 채워준다.
     */
    private CoachingSession persistedSession() {
        CoachingSession session = CoachingSession.create(submissionId, callerId, problemId);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    /**
     * generateFollowUpQuestion()이 explanation.getId()를 FollowUpQuestion의 FK로 쓰므로,
     * mock인 explanationRepository.save()가 실제 Hibernate처럼 ID를 채워서 돌려줘야 한다.
     */
    private Explanation persistedExplanation(Explanation explanation) {
        ReflectionTestUtils.setField(explanation, "id", UUID.randomUUID());
        return explanation;
    }

    @Test
    void 본인_소유가_아닌_제출이면_SUBMISSION_NOT_FOUND() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(UUID.randomUUID()));

        assertThatThrownBy(() -> facade.registerExplanation(submissionId, "설명", callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND);

        verify(explanationRepository, never()).save(any());
    }

    @Test
    void 본인_소유이지만_정답이_아니면_EXPLANATION_NOT_ALLOWED() {
        SubmissionSnapshot wrong = new SubmissionSnapshot(submissionId, callerId, problemId, "code", "WRONG", List.of(), 1);
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrong);

        assertThatThrownBy(() -> facade.registerExplanation(submissionId, "설명", callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXPLANATION_NOT_ALLOWED);

        verify(explanationRepository, never()).save(any());
    }

    @Test
    void 최초_등록이면_세션을_새로_만들고_설명과_역질문을_함께_반환한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.empty());
        CoachingSession newSession = persistedSession();
        when(coachingSessionRepository.save(any())).thenReturn(newSession);
        when(explanationRepository.save(any())).thenAnswer(inv -> persistedExplanation(inv.getArgument(0)));
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("반복문의 종료 조건은?", "claude-sonnet-5", 42));
        when(followUpQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                facade.registerExplanation(submissionId, "이 코드는 반복문으로 배열을 순회합니다.", callerId);

        assertThat(result.explanation().getCoachingSessionId()).isEqualTo(newSession.getId());
        assertThat(result.explanation().getSubmissionId()).isEqualTo(submissionId);
        assertThat(result.explanation().getContent()).isEqualTo("이 코드는 반복문으로 배열을 순회합니다.");
        assertThat(result.followUpQuestion().getQuestionText()).isEqualTo("반복문의 종료 조건은?");
        verify(aiCallHistoryRepository).save(any());
    }

    @Test
    void 기존_세션이_있으면_재사용해서_설명을_저장한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenAnswer(inv -> persistedExplanation(inv.getArgument(0)));
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("역질문", "claude-sonnet-5", 10));
        when(followUpQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        facade.registerExplanation(submissionId, "설명", callerId);

        verify(coachingSessionRepository, never()).save(any());
    }

    @Test
    void 이미_같은_제출에_설명이_등록돼있으면_EXPLANATION_ALREADY_EXISTS가_그대로_전파된다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenThrow(new BusinessException(ErrorCode.EXPLANATION_ALREADY_EXISTS));

        assertThatThrownBy(() -> facade.registerExplanation(submissionId, "설명", callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXPLANATION_ALREADY_EXISTS);

        verify(aiModelPort, never()).generate(any(), any());
    }

    /**
     * API 명세 — AI 역질문 생성이 실패해도 이미 저장된 설명은 그대로 유지하고
     * followUpQuestion만 null로 반환한다(예외를 던지지 않음). HintGenerationFacade가
     * LLM 실패 시 AI_GENERATION_FAILED를 던지는 것과 다르다 — 힌트는 그 자체가
     * 산출물이라 실패하면 줄 게 없지만, 설명은 이미 사용자가 작성한 본문이 핵심 산출물이고
     * 역질문은 부가 기능이라 실패해도 등록 자체는 성공으로 본다.
     */
    @Test
    void AI_역질문_생성이_실패해도_설명은_저장된_채로_반환하고_예외를_던지지_않는다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenAnswer(inv -> persistedExplanation(inv.getArgument(0)));
        when(aiModelPort.generate(any(), any())).thenThrow(new AiModelCallException("timeout", new RuntimeException()));

        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                facade.registerExplanation(submissionId, "설명", callerId);

        assertThat(result.explanation()).isNotNull();
        assertThat(result.followUpQuestion()).isNull();
        verify(aiCallHistoryRepository).save(argThatFailed());
        verify(followUpQuestionRepository, never()).save(any());
    }

    @Test
    void AI가_빈_응답을_반환해도_설명은_저장된_채로_반환하고_역질문만_null이다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenAnswer(inv -> persistedExplanation(inv.getArgument(0)));
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("   ", "claude-sonnet-5", 1));

        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                facade.registerExplanation(submissionId, "설명", callerId);

        assertThat(result.explanation()).isNotNull();
        assertThat(result.followUpQuestion()).isNull();
    }

    private AiCallHistory argThatFailed() {
        return org.mockito.ArgumentMatchers.argThat(history -> "FAILED".equals(history.getRequestStatus()));
    }
}
