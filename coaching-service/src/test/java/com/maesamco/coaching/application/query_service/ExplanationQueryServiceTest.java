package com.maesamco.coaching.application.query_service;

import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExplanationQueryServiceTest {

    @Mock
    private JudgeServicePort judgeServicePort;
    @Mock
    private ExplanationRepository explanationRepository;
    @Mock
    private FollowUpQuestionRepository followUpQuestionRepository;
    @Mock
    private FollowUpAnswerRepository followUpAnswerRepository;

    private ExplanationQueryService queryService;

    private final UUID submissionId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queryService = new ExplanationQueryService(
                judgeServicePort, explanationRepository, followUpQuestionRepository, followUpAnswerRepository
        );
    }

    private SubmissionSnapshot submission(UUID owner) {
        return new SubmissionSnapshot(submissionId, owner, problemId, "code", "CORRECT", List.of(), 1);
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    @Test
    void 본인_소유가_아닌_제출이면_SUBMISSION_NOT_FOUND() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(submission(UUID.randomUUID()));

        assertThatThrownBy(() -> queryService.getExplanation(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND);
    }

    @Test
    void 본인_제출이지만_등록된_설명이_없으면_EXPLANATION_NOT_FOUND() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(submission(callerId));
        when(explanationRepository.findBySubmissionId(submissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getExplanation(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXPLANATION_NOT_FOUND);
    }

    @Test
    void 역질문이_아직_없으면_followUpQuestion과_followUpAnswer_둘_다_null이다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(submission(callerId));
        Explanation explanation = withId(Explanation.create(UUID.randomUUID(), submissionId, "설명"));
        when(explanationRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(explanation));
        when(followUpQuestionRepository.findByExplanationId(explanation.getId())).thenReturn(Optional.empty());

        ExplanationQueryService.ExplanationQueryResult result = queryService.getExplanation(submissionId, callerId);

        assertThat(result.explanation()).isSameAs(explanation);
        assertThat(result.followUpQuestion()).isNull();
        assertThat(result.followUpAnswer()).isNull();
    }

    @Test
    void 역질문은_있지만_아직_답변하지_않았으면_followUpAnswer만_null이다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(submission(callerId));
        Explanation explanation = withId(Explanation.create(UUID.randomUUID(), submissionId, "설명"));
        when(explanationRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(explanation));
        FollowUpQuestion followUpQuestion = withId(FollowUpQuestion.create(explanation.getId(), "질문", "경계값"));
        when(followUpQuestionRepository.findByExplanationId(explanation.getId())).thenReturn(Optional.of(followUpQuestion));
        when(followUpAnswerRepository.findByFollowUpQuestionId(followUpQuestion.getId())).thenReturn(Optional.empty());

        ExplanationQueryService.ExplanationQueryResult result = queryService.getExplanation(submissionId, callerId);

        assertThat(result.followUpQuestion()).isSameAs(followUpQuestion);
        assertThat(result.followUpAnswer()).isNull();
    }

    @Test
    void 답변까지_있으면_전부_함께_반환한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(submission(callerId));
        Explanation explanation = withId(Explanation.create(UUID.randomUUID(), submissionId, "설명"));
        when(explanationRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(explanation));
        FollowUpQuestion followUpQuestion = withId(FollowUpQuestion.create(explanation.getId(), "질문", "경계값"));
        when(followUpQuestionRepository.findByExplanationId(explanation.getId())).thenReturn(Optional.of(followUpQuestion));
        FollowUpAnswer followUpAnswer = FollowUpAnswer.create(followUpQuestion.getId(), "답변");
        when(followUpAnswerRepository.findByFollowUpQuestionId(followUpQuestion.getId())).thenReturn(Optional.of(followUpAnswer));

        ExplanationQueryService.ExplanationQueryResult result = queryService.getExplanation(submissionId, callerId);

        assertThat(result.explanation()).isSameAs(explanation);
        assertThat(result.followUpQuestion()).isSameAs(followUpQuestion);
        assertThat(result.followUpAnswer()).isSameAs(followUpAnswer);
    }
}
