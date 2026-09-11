package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.persistence_service.FeedbackPersistenceService;
import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
import com.maesamco.coaching.application.port.ContentServicePort;
import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.ProblemSnapshot;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PR #98 자가 리뷰(용현님 P1) 반영 후 재작성 — AiFeedback/WeakConcept 저장 로직은
 * FeedbackPersistenceService로 옮겨졌으므로, 이 테스트는 그 저장 호출 여부·인자와
 * AiCallHistory 기록 여부만 검증한다. 실제 저장/WeakConcept 갱신 로직 자체는
 * FeedbackPersistenceServiceTest(Testcontainers)가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackGenerationFacadeTest {

    @Mock
    private JudgeServicePort judgeServicePort;
    @Mock
    private ContentServicePort contentServicePort;
    @Mock
    private AiModelPort aiModelPort;
    @Mock
    private AiCallHistoryRepository aiCallHistoryRepository;
    @Mock
    private FeedbackPersistenceService feedbackPersistenceService;

    private FeedbackGenerationFacade facade;

    private final UUID submissionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();
    private final ProblemSnapshot problemSnapshot = new ProblemSnapshot(problemId, "문제 설명", List.of("재귀"));

    private CoachingSession session;
    private Explanation explanation;
    private FollowUpQuestion followUpQuestion;
    private FollowUpAnswer followUpAnswer;

    @BeforeEach
    void setUp() {
        facade = new FeedbackGenerationFacade(judgeServicePort, contentServicePort, aiModelPort, aiCallHistoryRepository, feedbackPersistenceService);
        // 이슈 #62 — Content Service 연동 자체가 검증 대상이 아닌 테스트는 기본적으로
        // 정상 응답을 받는다. 문제 조회 실패를 직접 검증하는 테스트에서만 재정의한다.
        org.mockito.Mockito.lenient().when(contentServicePort.getProblem(any())).thenReturn(problemSnapshot);

        session = CoachingSession.create(submissionId, userId, problemId, 1);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        session.complete();

        explanation = Explanation.create(session.getId(), submissionId, "설명 내용");
        ReflectionTestUtils.setField(explanation, "id", UUID.randomUUID());

        followUpQuestion = FollowUpQuestion.create(explanation.getId(), "질문 내용", null);
        ReflectionTestUtils.setField(followUpQuestion, "id", UUID.randomUUID());

        followUpAnswer = FollowUpAnswer.create(followUpQuestion.getId(), "답변 내용");
        ReflectionTestUtils.setField(followUpAnswer, "id", UUID.randomUUID());
    }

    private void stubSubmission() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(
                new SubmissionSnapshot(submissionId, userId, problemId, "public class Main {}", "CORRECT", List.of(), 1)
        );
    }

    @Test
    void JSON이_정상_파싱되면_파싱된_값_그대로_저장을_요청한다() {
        stubSubmission();
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse(
                """
                {"understoodConcepts":["반복문"],"explanationGaps":["경계값 처리"],
                 "weakConcepts":["재귀"],"syntaxToImprove":["변수명"],
                 "recommendedProblems":["이분탐색"],"nextDirection":"재귀를 복습하세요"}
                """,
                "claude-sonnet-5", 30
        ));

        facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer);

        verify(feedbackPersistenceService).saveFeedback(
                eq(session.getId()), eq(userId), eq("claude-sonnet-5"), anyString(), eq(30),
                argThat(node -> node.get(0).asString().equals("반복문")),
                any(JsonNode.class),
                argThat(node -> node.get(0).asString().equals("재귀")),
                any(), any(), eq("재귀를 복습하세요")
        );
        verify(aiCallHistoryRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_제출이면_저장을_시도하지_않고_FAILED_이력을_남긴다() {
        when(judgeServicePort.getSubmission(submissionId)).thenThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verifyNoInteractions(aiModelPort, feedbackPersistenceService);
        verify(aiCallHistoryRepository).save(argThat(h -> "FAILED".equals(h.getRequestStatus())));
    }

    @Test
    void 저장_단계에서_예외가_나면_FAILED_이력을_남기고_예외를_삼킨다() {
        stubSubmission();
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse(
                "{\"understoodConcepts\":[\"반복문\"],\"explanationGaps\":[],"
                        + "\"weakConcepts\":[\"재귀\"],\"syntaxToImprove\":null,"
                        + "\"recommendedProblems\":null,\"nextDirection\":null}",
                "claude-sonnet-5", 30
        ));
        doThrow(new RuntimeException("DB 오류")).when(feedbackPersistenceService)
                .saveFeedback(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verify(aiCallHistoryRepository).save(argThat(h -> "FAILED".equals(h.getRequestStatus())));
    }

    @Test
    void JSON_파싱에_실패하면_예외_없이_종료하고_저장을_시도하지_않는다() {
        stubSubmission();
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("이건 JSON이 아닙니다", "claude-sonnet-5", 5));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verifyNoInteractions(feedbackPersistenceService);
        verify(aiCallHistoryRepository).save(argThat(h -> "FAILED".equals(h.getRequestStatus())));
    }

    @Test
    void 필수_필드가_배열이_아니면_예외_없이_종료하고_저장을_시도하지_않는다() {
        stubSubmission();
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse(
                "{\"understoodConcepts\":\"반복문\",\"explanationGaps\":[],\"weakConcepts\":[]}",
                "claude-sonnet-5", 5
        ));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verifyNoInteractions(feedbackPersistenceService);
    }

    @Test
    void LLM_호출이_실패해도_예외_없이_종료한다() {
        stubSubmission();
        when(aiModelPort.generate(any(), any())).thenThrow(new AiModelCallException("timeout", new RuntimeException()));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verifyNoInteractions(feedbackPersistenceService);
        verify(aiCallHistoryRepository).save(argThat(h -> "FAILED".equals(h.getRequestStatus())));
    }

    /**
     * 재검증(PR #111, 외부 AI 리뷰) — 서킷브레이커가 열려서 실제 LLM 호출 자체가 없었던
     * 경우(circuitOpen=true)는 "FAILED"가 아니라 "SKIPPED"로 남겨야
     * AiFeedbackRetryFacade의 재시도 카운트에서 제외된다. 힌트/역질문 생성 쪽 장애로 서킷이
     * 열렸을 때, 이 사용자의 피드백 재시도 예산이 실제 시도 없이 소모되는 걸 막기 위함.
     */
    @Test
    void 서킷브레이커가_열려서_호출이_차단되면_FAILED_대신_SKIPPED_이력을_남긴다() {
        stubSubmission();
        when(aiModelPort.generate(any(), any()))
                .thenThrow(new AiModelCallException("Claude 호출이 차단되었습니다(circuit open).", new RuntimeException(), true));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verifyNoInteractions(feedbackPersistenceService);
        verify(aiCallHistoryRepository).save(argThat(h -> "SKIPPED".equals(h.getRequestStatus())));
    }

    /**
     * 용현님 리뷰(P1) — session.getSubmissionId()가 재도전으로 갈아탄 뒤에도,
     * explanation.getSubmissionId()(원래 설명이 달렸던 제출)로 조회해야 한다. 이 테스트는
     * 세션의 submissionId와 explanation의 submissionId를 의도적으로 다르게 둬서, 실제로
     * explanation 쪽 값으로 조회하는지 확인한다.
     */
    @Test
    void 세션의_최신_submissionId가_아니라_explanation의_submissionId로_제출을_조회한다() {
        UUID staleExplanationSubmissionId = submissionId;
        UUID latestSessionSubmissionId = UUID.randomUUID();

        CoachingSession sessionWithNewerSubmission =
                CoachingSession.create(latestSessionSubmissionId, userId, problemId, 1);
        ReflectionTestUtils.setField(sessionWithNewerSubmission, "id", session.getId());
        sessionWithNewerSubmission.complete();

        when(judgeServicePort.getSubmission(staleExplanationSubmissionId)).thenReturn(
                new SubmissionSnapshot(staleExplanationSubmissionId, userId, problemId, "public class Main {}", "CORRECT", List.of(), 1)
        );
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse(
                "{\"understoodConcepts\":[\"반복문\"],\"explanationGaps\":[],"
                        + "\"weakConcepts\":[],\"syntaxToImprove\":null,\"recommendedProblems\":null,\"nextDirection\":null}",
                "claude-sonnet-5", 5
        ));

        facade.generateFeedback(sessionWithNewerSubmission, explanation, followUpQuestion, followUpAnswer);

        verify(judgeServicePort).getSubmission(staleExplanationSubmissionId);
        verify(judgeServicePort, never()).getSubmission(latestSessionSubmissionId);
    }

    @Test
    void AI가_빈_응답을_반환해도_예외_없이_종료한다() {
        stubSubmission();
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("   ", "claude-sonnet-5", 1));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verifyNoInteractions(feedbackPersistenceService);
    }

    @Test
    void AI_응답이_마크다운_코드블록으로_감싸져_있어도_JSON을_파싱한다() {
        stubSubmission();
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse(
                "```json\n{\"understoodConcepts\":[\"반복문\"],\"explanationGaps\":[],"
                        + "\"weakConcepts\":[],\"syntaxToImprove\":[],\"recommendedProblems\":[],"
                        + "\"nextDirection\":\"계속 진행하세요\"}\n```",
                "claude-sonnet-5", 10
        ));

        facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer);

        verify(feedbackPersistenceService).saveFeedback(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("계속 진행하세요")
        );
    }

    // ===== 이슈 #62/#126 — Content Service 연동 =====

    @Test
    void 피드백_생성_시_문제_설명을_프롬프트에_포함한다() {
        stubSubmission();
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse(
                "{\"understoodConcepts\":[\"반복문\"],\"explanationGaps\":[],"
                        + "\"weakConcepts\":[],\"syntaxToImprove\":null,\"recommendedProblems\":null,\"nextDirection\":null}",
                "claude-sonnet-5", 5
        ));

        facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer);

        verify(aiModelPort).generate(any(), argThat(userPrompt -> userPrompt.contains(problemSnapshot.description())));
    }

    /**
     * "지문 없이는 생성 시도 안 함" 정책(이슈 #126) — 이 Facade는 실패를 던지지 않고 전부
     * 삼키므로(클래스 Javadoc), Content Service 조회 실패도 다른 실패 경로와 동일하게
     * FAILED 이력만 남기고 조용히 반환한다.
     */
    @Test
    void 문제_조회에_실패하면_예외_없이_종료하고_FAILED_이력만_남긴다() {
        stubSubmission();
        when(contentServicePort.getProblem(problemId)).thenThrow(new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verify(aiModelPort, never()).generate(any(), any());
        verifyNoInteractions(feedbackPersistenceService);
        verify(aiCallHistoryRepository).save(argThat(h -> "FAILED".equals(h.getRequestStatus())));
    }
}
