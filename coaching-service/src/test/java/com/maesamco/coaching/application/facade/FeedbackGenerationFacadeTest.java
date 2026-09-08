package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.persistence_service.FeedbackPersistenceService;
import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
import com.maesamco.coaching.application.port.JudgeServicePort;
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
    private AiModelPort aiModelPort;
    @Mock
    private AiCallHistoryRepository aiCallHistoryRepository;
    @Mock
    private FeedbackPersistenceService feedbackPersistenceService;

    private FeedbackGenerationFacade facade;

    private final UUID submissionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();

    private CoachingSession session;
    private Explanation explanation;
    private FollowUpQuestion followUpQuestion;
    private FollowUpAnswer followUpAnswer;

    @BeforeEach
    void setUp() {
        facade = new FeedbackGenerationFacade(judgeServicePort, aiModelPort, aiCallHistoryRepository, feedbackPersistenceService);

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
}
