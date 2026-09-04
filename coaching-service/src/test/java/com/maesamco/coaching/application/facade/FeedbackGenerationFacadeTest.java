package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.AiFeedbackRepository;
import com.maesamco.coaching.domain.repository.WeakConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackGenerationFacadeTest {

    @Mock
    private JudgeServicePort judgeServicePort;
    @Mock
    private AiModelPort aiModelPort;
    @Mock
    private AiCallHistoryRepository aiCallHistoryRepository;
    @Mock
    private AiFeedbackRepository aiFeedbackRepository;
    @Mock
    private WeakConceptRepository weakConceptRepository;

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
        facade = new FeedbackGenerationFacade(
                judgeServicePort, aiModelPort, aiCallHistoryRepository, aiFeedbackRepository, weakConceptRepository
        );

        session = CoachingSession.create(submissionId, userId, problemId, 1);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        session.complete();

        explanation = Explanation.create(session.getId(), submissionId, "설명 내용");
        ReflectionTestUtils.setField(explanation, "id", UUID.randomUUID());

        followUpQuestion = FollowUpQuestion.create(explanation.getId(), "질문 내용", null);
        ReflectionTestUtils.setField(followUpQuestion, "id", UUID.randomUUID());

        followUpAnswer = FollowUpAnswer.create(followUpQuestion.getId(), "답변 내용");
        ReflectionTestUtils.setField(followUpAnswer, "id", UUID.randomUUID());

        when(judgeServicePort.getSubmission(submissionId)).thenReturn(
                new SubmissionSnapshot(submissionId, userId, problemId, "public class Main {}", "CORRECT", List.of(), 1)
        );
    }

    @Test
    void JSON이_정상_파싱되면_피드백을_저장하고_새_취약개념을_기록한다() {
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse(
                """
                {"understoodConcepts":["반복문"],"explanationGaps":["경계값 처리"],
                 "weakConcepts":["재귀"],"syntaxToImprove":["변수명"],
                 "recommendedProblems":["이분탐색"],"nextDirection":"재귀를 복습하세요"}
                """,
                "claude-sonnet-5", 30
        ));
        when(weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀")).thenReturn(Optional.empty());
        when(weakConceptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer);

        verify(aiFeedbackRepository).save(any());
        verify(aiCallHistoryRepository).save(argThat(h -> "SUCCESS".equals(h.getRequestStatus())));
        ArgumentCaptor<WeakConcept> captor = ArgumentCaptor.forClass(WeakConcept.class);
        verify(weakConceptRepository).save(captor.capture());
        assertThat(captor.getValue().getConceptTag()).isEqualTo("재귀");
        assertThat(captor.getValue().getOccurrenceCount()).isEqualTo(1);
    }

    @Test
    void 이미_존재하는_취약개념이면_새로_만들지_않고_발견_횟수만_갱신한다() {
        WeakConcept existing = WeakConcept.create(userId, "재귀");
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse(
                "{\"understoodConcepts\":[\"반복문\"],\"explanationGaps\":[],"
                        + "\"weakConcepts\":[\"재귀\"],\"syntaxToImprove\":null,"
                        + "\"recommendedProblems\":null,\"nextDirection\":null}",
                "claude-sonnet-5", 30
        ));
        when(weakConceptRepository.findByUserIdAndConceptTag(userId, "재귀")).thenReturn(Optional.of(existing));

        facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer);

        verify(weakConceptRepository, never()).save(argThat(w -> w != existing));
        verify(weakConceptRepository).save(existing);
        assertThat(existing.getOccurrenceCount()).isEqualTo(2);
    }

    @Test
    void JSON_파싱에_실패하면_예외_없이_종료하고_피드백을_저장하지_않는다() {
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("이건 JSON이 아닙니다", "claude-sonnet-5", 5));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verify(aiFeedbackRepository, never()).save(any());
        verify(weakConceptRepository, never()).save(any());
        verify(aiCallHistoryRepository).save(argThat(h -> "FAILED".equals(h.getRequestStatus())));
    }

    @Test
    void 필수_필드가_배열이_아니면_예외_없이_종료하고_피드백을_저장하지_않는다() {
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse(
                "{\"understoodConcepts\":\"반복문\",\"explanationGaps\":[],\"weakConcepts\":[]}",
                "claude-sonnet-5", 5
        ));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verify(aiFeedbackRepository, never()).save(any());
    }

    @Test
    void LLM_호출이_실패해도_예외_없이_종료한다() {
        when(aiModelPort.generate(any(), any())).thenThrow(new AiModelCallException("timeout", new RuntimeException()));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verify(aiFeedbackRepository, never()).save(any());
        verify(aiCallHistoryRepository).save(argThat(h -> "FAILED".equals(h.getRequestStatus())));
    }

    @Test
    void AI가_빈_응답을_반환해도_예외_없이_종료한다() {
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("   ", "claude-sonnet-5", 1));

        assertThatCode(() -> facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer))
                .doesNotThrowAnyException();

        verify(aiFeedbackRepository, never()).save(any());
    }

    @Test
    void AI_응답이_마크다운_코드블록으로_감싸져_있어도_JSON을_파싱한다() {
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse(
                "```json\n{\"understoodConcepts\":[\"반복문\"],\"explanationGaps\":[],"
                        + "\"weakConcepts\":[],\"syntaxToImprove\":[],\"recommendedProblems\":[],"
                        + "\"nextDirection\":\"계속 진행하세요\"}\n```",
                "claude-sonnet-5", 10
        ));

        facade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer);

        verify(aiFeedbackRepository).save(any());
    }
}
