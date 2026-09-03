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
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
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
        CoachingSession session = CoachingSession.create(submissionId, callerId, problemId, 1);
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

    /**
     * 재교차검증 리뷰(3a/3b) 대응 — EXPLANATION_ALREADY_EXISTS를 더 이상 그대로 전파하지
     * 않고 retryExistingExplanation()으로 위임한다. 아래 세 테스트가 그 분기를 검증한다.
     */
    @Test
    void 이미_설명이_있고_역질문도_있으면_재생성_없이_기존_결과를_그대로_반환한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenThrow(new BusinessException(ErrorCode.EXPLANATION_ALREADY_EXISTS));

        Explanation existingExplanation =
                persistedExplanation(Explanation.create(existingSession.getId(), submissionId, "이전 설명"));
        when(explanationRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(existingExplanation));
        FollowUpQuestion existingFollowUpQuestion =
                FollowUpQuestion.create(existingExplanation.getId(), "질문", "경계값");
        when(followUpQuestionRepository.findByExplanationId(existingExplanation.getId()))
                .thenReturn(Optional.of(existingFollowUpQuestion));

        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                facade.registerExplanation(submissionId, "설명", callerId);

        assertThat(result.created()).isFalse();
        assertThat(result.explanation()).isSameAs(existingExplanation);
        assertThat(result.followUpQuestion()).isSameAs(existingFollowUpQuestion);
        verify(aiModelPort, never()).generate(any(), any());
    }

    @Test
    void 이미_설명은_있지만_역질문이_없으면_역질문_생성만_재시도한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenThrow(new BusinessException(ErrorCode.EXPLANATION_ALREADY_EXISTS));

        Explanation existingExplanation =
                persistedExplanation(Explanation.create(existingSession.getId(), submissionId, "이전 설명"));
        when(explanationRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(existingExplanation));
        when(followUpQuestionRepository.findByExplanationId(existingExplanation.getId())).thenReturn(Optional.empty());
        when(aiModelPort.generate(any(), any())).thenReturn(
                new AiModelResponse("{\"category\": \"경계값\", \"question\": \"재시도로 생성된 질문\"}", "claude-sonnet-5", 10)
        );
        when(followUpQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                facade.registerExplanation(submissionId, "설명", callerId);

        assertThat(result.created()).isFalse();
        assertThat(result.explanation()).isSameAs(existingExplanation);
        assertThat(result.followUpQuestion().getQuestionText()).isEqualTo("재시도로 생성된 질문");
    }

    @Test
    void 재시도에서도_AI가_실패하면_역질문_없이_기존_설명을_그대로_반환한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenThrow(new BusinessException(ErrorCode.EXPLANATION_ALREADY_EXISTS));

        Explanation existingExplanation =
                persistedExplanation(Explanation.create(existingSession.getId(), submissionId, "이전 설명"));
        when(explanationRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(existingExplanation));
        when(followUpQuestionRepository.findByExplanationId(existingExplanation.getId())).thenReturn(Optional.empty());
        when(aiModelPort.generate(any(), any())).thenThrow(new AiModelCallException("timeout", new RuntimeException()));

        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                facade.registerExplanation(submissionId, "설명", callerId);

        assertThat(result.created()).isFalse();
        assertThat(result.explanation()).isSameAs(existingExplanation);
        assertThat(result.followUpQuestion()).isNull();
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
    void AI가_JSON으로_응답하면_category와_question을_분리해서_저장한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenAnswer(inv -> persistedExplanation(inv.getArgument(0)));
        when(aiModelPort.generate(any(), any())).thenReturn(
                new AiModelResponse("{\"category\": \"경계값\", \"question\": \"배열이 비어있을 때는 어떻게 되나요?\"}",
                        "claude-sonnet-5", 20)
        );
        when(followUpQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                facade.registerExplanation(submissionId, "설명", callerId);

        assertThat(result.followUpQuestion().getQuestionText()).isEqualTo("배열이 비어있을 때는 어떻게 되나요?");
        assertThat(result.followUpQuestion().getCategory()).isEqualTo("경계값");
    }

    @Test
    void AI_응답이_마크다운_코드블록으로_감싸져_있어도_JSON을_파싱한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenAnswer(inv -> persistedExplanation(inv.getArgument(0)));
        when(aiModelPort.generate(any(), any())).thenReturn(
                new AiModelResponse("```json\n{\"category\": \"자료구조\", \"question\": \"왜 배열을 썼나요?\"}\n```",
                        "claude-sonnet-5", 20)
        );
        when(followUpQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                facade.registerExplanation(submissionId, "설명", callerId);

        assertThat(result.followUpQuestion().getQuestionText()).isEqualTo("왜 배열을 썼나요?");
        assertThat(result.followUpQuestion().getCategory()).isEqualTo("자료구조");
    }

    @Test
    void AI가_JSON이_아닌_평문으로_응답해도_전체를_질문으로_저장하고_category는_null이다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenAnswer(inv -> persistedExplanation(inv.getArgument(0)));
        when(aiModelPort.generate(any(), any())).thenReturn(
                new AiModelResponse("반복문의 종료 조건은 무엇인가요?", "claude-sonnet-5", 20)
        );
        when(followUpQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                facade.registerExplanation(submissionId, "설명", callerId);

        assertThat(result.followUpQuestion().getQuestionText()).isEqualTo("반복문의 종료 조건은 무엇인가요?");
        assertThat(result.followUpQuestion().getCategory()).isNull();
    }

    /**
     * PR #88 리뷰(용현님 P2) 대응 — category가 DB 컬럼 한도(30자)를 넘으면
     * FollowUpQuestion.create()가 BusinessException을 던진다. 모델이 "한 단어" 지시를
     * 안 지켜도 그 포맷 실수 때문에 이미 저장된 설명까지 포함한 요청 전체가 깨지면 안
     * 된다 — category만 버리고 questionText·등록 자체는 그대로 성공해야 한다.
     */
    @Test
    void category가_30자를_초과하면_category만_버리고_등록은_그대로_성공한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correctSubmission(callerId));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(explanationRepository.save(any())).thenAnswer(inv -> persistedExplanation(inv.getArgument(0)));
        String tooLongCategory = "가".repeat(31);
        when(aiModelPort.generate(any(), any())).thenReturn(
                new AiModelResponse("{\"category\": \"" + tooLongCategory + "\", \"question\": \"질문\"}",
                        "claude-sonnet-5", 20)
        );
        when(followUpQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExplanationGenerationFacade.ExplanationRegistrationResult result =
                facade.registerExplanation(submissionId, "설명", callerId);

        assertThat(result.explanation()).isNotNull();
        assertThat(result.followUpQuestion().getQuestionText()).isEqualTo("질문");
        assertThat(result.followUpQuestion().getCategory()).isNull();
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
