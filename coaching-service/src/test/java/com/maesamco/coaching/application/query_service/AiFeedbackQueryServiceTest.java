package com.maesamco.coaching.application.query_service;

import com.maesamco.coaching.application.facade.AiFeedbackRetryFacade;
import com.maesamco.coaching.domain.entity.AiCallPurpose;
import com.maesamco.coaching.domain.entity.AiFeedback;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.AiFeedbackRepository;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiFeedbackQueryServiceTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private CoachingSessionRepository coachingSessionRepository;
    @Mock
    private AiFeedbackRepository aiFeedbackRepository;
    @Mock
    private AiCallHistoryRepository aiCallHistoryRepository;

    private AiFeedbackQueryService queryService;

    private final UUID submissionId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queryService = new AiFeedbackQueryService(coachingSessionRepository, aiFeedbackRepository, aiCallHistoryRepository);
    }

    /** 완료된 세션 — 대부분의 테스트가 "완료 이후" 시나리오(피드백 있음/없음)를 다룬다. */
    private CoachingSession session(UUID owner) {
        CoachingSession session = CoachingSession.create(submissionId, owner, problemId, 1);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        session.complete();
        return session;
    }

    /** 재검증(PR #111) — AI_FEEDBACK_NOT_STARTED 검증용, 완료 전(status=IN_PROGRESS) 세션. */
    private CoachingSession inProgressSession(UUID owner) {
        CoachingSession session = CoachingSession.create(submissionId, owner, problemId, 1);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    private AiFeedback feedback(UUID coachingSessionId) {
        ArrayNode emptyArray = JSON_MAPPER.createArrayNode();
        return AiFeedback.create(coachingSessionId, emptyArray, emptyArray, emptyArray, null, null, "다음 방향");
    }

    @Test
    void 세션_자체가_없으면_SUBMISSION_NOT_FOUND() {
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND);
    }

    @Test
    void 본인_소유가_아닌_세션이면_SUBMISSION_NOT_FOUND() {
        when(coachingSessionRepository.findBySubmissionId(submissionId))
                .thenReturn(Optional.of(session(UUID.randomUUID())));

        assertThatThrownBy(() -> queryService.getFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND);
    }

    @Test
    void 본인_세션이지만_피드백이_아직_없으면_AI_FEEDBACK_NOT_FOUND() {
        CoachingSession session = session(callerId);
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRepository.findByCoachingSessionId(session.getId())).thenReturn(Optional.empty());
        when(aiCallHistoryRepository.countRealAttemptsByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
                .thenReturn(1L);

        assertThatThrownBy(() -> queryService.getFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_FEEDBACK_NOT_FOUND);
    }

    /** 재검증(PR #111, 외부 AI 리뷰) — 세션이 아직 완료 전이면 재시도 예산과 무관하게 항상 이 코드다. */
    @Test
    void 세션이_아직_완료되지_않았으면_AI_FEEDBACK_NOT_STARTED() {
        CoachingSession session = inProgressSession(callerId);
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> queryService.getFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_FEEDBACK_NOT_STARTED);
    }

    /** 재검증(PR #111, 외부 AI 리뷰) — 재시도 예산(4회)까지 소진됐으면 별도 코드로 구분한다. */
    @Test
    void 재시도_예산이_소진됐으면_AI_FEEDBACK_RETRY_LIMIT_EXCEEDED() {
        CoachingSession session = session(callerId);
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRepository.findByCoachingSessionId(session.getId())).thenReturn(Optional.empty());
        when(aiCallHistoryRepository.countRealAttemptsByCoachingSessionIdAndPurpose(session.getId(), AiCallPurpose.FEEDBACK))
                .thenReturn(AiFeedbackRetryFacade.MAX_ATTEMPTS);

        assertThatThrownBy(() -> queryService.getFeedback(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_FEEDBACK_RETRY_LIMIT_EXCEEDED);
    }

    @Test
    void 정상_조회() {
        CoachingSession session = session(callerId);
        AiFeedback feedback = feedback(session.getId());
        when(coachingSessionRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(session));
        when(aiFeedbackRepository.findByCoachingSessionId(session.getId())).thenReturn(Optional.of(feedback));

        AiFeedback result = queryService.getFeedback(submissionId, callerId);

        assertThat(result).isSameAs(feedback);
    }
}
