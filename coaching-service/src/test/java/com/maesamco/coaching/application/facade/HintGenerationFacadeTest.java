package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.port.AiModelCallException;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
import com.maesamco.coaching.application.port.HintGenerationLockPort;
import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Hint;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.HintRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HintGenerationFacadeTest {

    @Mock
    private JudgeServicePort judgeServicePort;
    @Mock
    private CoachingSessionRepository coachingSessionRepository;
    @Mock
    private HintRepository hintRepository;
    @Mock
    private AiModelPort aiModelPort;
    @Mock
    private AiCallHistoryRepository aiCallHistoryRepository;
    @Mock
    private HintGenerationLockPort hintGenerationLockPort;

    private HintGenerationFacade facade;

    private final UUID submissionId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        facade = new HintGenerationFacade(
                judgeServicePort, coachingSessionRepository, hintRepository, aiModelPort, aiCallHistoryRepository,
                hintGenerationLockPort
        );
        // 대부분의 테스트는 락 자체를 검증 대상이 아니라 "항상 획득 성공"으로 두고 기존
        // 흐름만 본다 — 락 관련 테스트에서만 개별적으로 재정의한다. lenient()라 다른
        // 테스트에서 이 스텁을 안 써도 Mockito가 UnnecessaryStubbingException을 안 던진다.
        org.mockito.Mockito.lenient().when(hintGenerationLockPort.tryLock(any(), any())).thenReturn(true);
    }

    private SubmissionSnapshot wrongSubmission(UUID owner, int attemptNo) {
        return new SubmissionSnapshot(submissionId, owner, problemId, "public class Main {}", "WRONG", List.of(), attemptNo);
    }

    /**
     * CoachingSession.create()는 트랜지언트 엔티티라 id가 null이다(실제로는 Hibernate가
     * saveAndFlush 시점에 GenerationType.UUID로 채워줌). Repository를 목으로 대체한
     * 단위 테스트에는 그 과정이 없으므로, Hibernate가 하는 일을 여기서 대신 시뮬레이션한다.
     */
    private CoachingSession persistedSession() {
        CoachingSession session = CoachingSession.create(submissionId, callerId, problemId);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    @Test
    void 본인_소유가_아닌_제출이면_SUBMISSION_NOT_FOUND() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(UUID.randomUUID(), 1));

        assertThatThrownBy(() -> facade.requestHint(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND);

        verify(coachingSessionRepository, never()).save(any());
    }

    @Test
    void 본인_소유이지만_오답이_아니면_HINT_NOT_ALLOWED() {
        SubmissionSnapshot correct = new SubmissionSnapshot(submissionId, callerId, problemId, "code", "CORRECT", List.of(), 1);
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(correct);

        assertThatThrownBy(() -> facade.requestHint(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HINT_NOT_ALLOWED);
    }

    /**
     * PR #70 리뷰 — WRONG뿐 아니라 COMPILE_ERROR/RUNTIME_ERROR/
     * TIME_LIMIT_EXCEEDED/MEMORY_LIMIT_EXCEEDED도 힌트 요청 대상이어야 한다
     * (judge-service SubmissionResult enum, 매삼코_DB_테이블_명세 04절).
     */
    @ParameterizedTest
    @ValueSource(strings = {"WRONG", "COMPILE_ERROR", "RUNTIME_ERROR", "TIME_LIMIT_EXCEEDED", "MEMORY_LIMIT_EXCEEDED"})
    void 오답으로_분류되는_모든_result값에_대해_힌트를_생성한다(String result) {
        SubmissionSnapshot submission = new SubmissionSnapshot(submissionId, callerId, problemId, "code", result, List.of(), 1);
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(submission);
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of());
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("힌트", "claude-sonnet-5", 1));
        when(hintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HintGenerationFacade.HintGenerationResult generationResult = facade.requestHint(submissionId, callerId);

        assertThat(generationResult.hint().getStage()).isEqualTo(1);
    }

    @Test
    void result가_null이면_아직_채점_중이므로_HINT_NOT_ALLOWED() {
        SubmissionSnapshot pending = new SubmissionSnapshot(submissionId, callerId, problemId, "code", null, List.of(), 0);
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(pending);

        assertThatThrownBy(() -> facade.requestHint(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HINT_NOT_ALLOWED);
    }

    @Test
    void 최초_요청이면_세션을_새로_만들고_1단계_힌트를_생성한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 1));
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.empty());
        CoachingSession newSession = persistedSession();
        when(coachingSessionRepository.save(any())).thenReturn(newSession);
        when(hintRepository.findByCoachingSessionId(newSession.getId())).thenReturn(List.of());
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("1단계 힌트 내용", "claude-sonnet-5", 42));
        when(hintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HintGenerationFacade.HintGenerationResult result = facade.requestHint(submissionId, callerId);

        assertThat(result.created()).isTrue();
        assertThat(result.hint().getStage()).isEqualTo(1);
        assertThat(result.hint().getContent()).isEqualTo("1단계 힌트 내용");
        assertThat(result.skipAvailable()).isFalse();
        verify(aiCallHistoryRepository).save(any());
    }

    @Test
    void 이미_1단계_힌트가_있으면_2단계를_생성한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 2));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        Hint stage1 = Hint.create(existingSession.getId(), 1, "1단계");
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of(stage1));
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("2단계 힌트", "claude-sonnet-5", 10));
        when(hintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HintGenerationFacade.HintGenerationResult result = facade.requestHint(submissionId, callerId);

        assertThat(result.hint().getStage()).isEqualTo(2);
        verify(coachingSessionRepository, never()).save(any());
    }

    @Test
    void 이미_4단계까지_있으면_새로_생성하지_않고_기존_4단계를_그대로_반환한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 5));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        Hint stage4 = Hint.create(existingSession.getId(), 4, "4단계 — 수정 방향");
        when(hintRepository.findByCoachingSessionId(existingSession.getId()))
                .thenReturn(List.of(Hint.create(existingSession.getId(), 1, "1"), stage4));

        HintGenerationFacade.HintGenerationResult result = facade.requestHint(submissionId, callerId);

        assertThat(result.created()).isFalse();
        assertThat(result.hint().getStage()).isEqualTo(4);
        assertThat(result.hint()).isSameAs(stage4);
        verify(aiModelPort, never()).generate(any(), any());
        verify(hintRepository, never()).save(any());
    }

    @Test
    void attemptNo가_8이상이면_skipAvailable이_true다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 8));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of());
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("힌트", "claude-sonnet-5", 1));
        when(hintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HintGenerationFacade.HintGenerationResult result = facade.requestHint(submissionId, callerId);

        assertThat(result.skipAvailable()).isTrue();
    }

    @Test
    void attemptNo가_8미만이면_skipAvailable이_false다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 7));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of());
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("힌트", "claude-sonnet-5", 1));
        when(hintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HintGenerationFacade.HintGenerationResult result = facade.requestHint(submissionId, callerId);

        assertThat(result.skipAvailable()).isFalse();
    }

    @Test
    void LLM_호출이_실패하면_AI_GENERATION_FAILED로_변환하고_실패_이력을_남긴다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 1));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of());
        when(aiModelPort.generate(any(), any())).thenThrow(new AiModelCallException("timeout", new RuntimeException()));

        assertThatThrownBy(() -> facade.requestHint(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_GENERATION_FAILED);

        verify(aiCallHistoryRepository).save(argThatFailed());
        verify(hintRepository, never()).save(any());
    }

    private AiCallHistory argThatFailed() {
        return org.mockito.ArgumentMatchers.argThat(history -> "FAILED".equals(history.getRequestStatus()));
    }

    @Test
    void 재시도로_submissionId가_바뀌면_세션의_submissionId를_최신으로_갱신한다() {
        UUID newSubmissionId = UUID.randomUUID();
        SubmissionSnapshot retrySubmission = new SubmissionSnapshot(newSubmissionId, callerId, problemId, "code", "WRONG", List.of(), 2);
        when(judgeServicePort.getSubmission(newSubmissionId)).thenReturn(retrySubmission);
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(coachingSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of());
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("2단계 힌트", "claude-sonnet-5", 10));
        when(hintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        facade.requestHint(newSubmissionId, callerId);

        assertThat(existingSession.getSubmissionId()).isEqualTo(newSubmissionId);
        verify(coachingSessionRepository).save(existingSession);
    }

    @Test
    void 동시_요청으로_같은_stage_힌트가_이미_저장됐으면_기존_힌트를_반환한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 1));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of());
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("1단계 힌트", "claude-sonnet-5", 1));
        when(hintRepository.save(any())).thenThrow(new BusinessException(ErrorCode.HINT_ALREADY_EXISTS));
        Hint winningHint = Hint.create(existingSession.getId(), 1, "다른 요청이 먼저 저장한 힌트");
        when(hintRepository.findByCoachingSessionIdAndStage(existingSession.getId(), 1)).thenReturn(Optional.of(winningHint));

        HintGenerationFacade.HintGenerationResult result = facade.requestHint(submissionId, callerId);

        assertThat(result.created()).isFalse();
        assertThat(result.hint()).isSameAs(winningHint);
    }

    /**
     * PR #70 리뷰(비용/어뷰징 관점) — 락을 못 얻으면(다른 요청이 이미 생성 중) LLM을
     * 아예 호출하지 않아야 한다. 기존 UNIQUE 제약 기반 복구는 "저장 실패 후 복구"라
     * LLM은 이미 호출된 뒤였는데, 이 테스트는 그 자체가 안 일어나는지를 검증한다.
     */
    @Test
    void 락을_못_얻으면_LLM을_호출하지_않고_다른_요청이_만든_힌트를_기다려서_반환한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 1));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of());
        when(hintGenerationLockPort.tryLock(eq(existingSession.getId()), any())).thenReturn(false);
        Hint concurrentlyCreatedHint = Hint.create(existingSession.getId(), 1, "다른 요청이 만든 힌트");
        when(hintRepository.findByCoachingSessionIdAndStage(existingSession.getId(), 1))
                .thenReturn(Optional.of(concurrentlyCreatedHint));

        HintGenerationFacade.HintGenerationResult result = facade.requestHint(submissionId, callerId);

        assertThat(result.created()).isFalse();
        assertThat(result.hint()).isSameAs(concurrentlyCreatedHint);
        verify(aiModelPort, never()).generate(any(), any());
        verify(hintGenerationLockPort, never()).unlock(any(), any()); // 락을 못 얻었으니 해제할 것도 없다
    }

    @Test
    void 락을_못_얻고_기다려도_힌트가_안_나타나면_AI_GENERATION_FAILED() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 1));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of());
        when(hintGenerationLockPort.tryLock(eq(existingSession.getId()), any())).thenReturn(false);
        when(hintRepository.findByCoachingSessionIdAndStage(existingSession.getId(), 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.requestHint(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_GENERATION_FAILED);

        verify(aiModelPort, never()).generate(any(), any());
    }

    @Test
    void 락을_얻으면_힌트_생성_후_반드시_해제한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 1));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of());
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("1단계 힌트", "claude-sonnet-5", 1));
        when(hintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        facade.requestHint(submissionId, callerId);

        verify(hintGenerationLockPort).tryLock(eq(existingSession.getId()), any());
        verify(hintGenerationLockPort).unlock(eq(existingSession.getId()), any());
    }

    @Test
    void AI가_빈_응답을_반환하면_AI_GENERATION_FAILED로_처리하고_힌트를_저장하지_않는다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 1));
        CoachingSession existingSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId)).thenReturn(Optional.of(existingSession));
        when(hintRepository.findByCoachingSessionId(existingSession.getId())).thenReturn(List.of());
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("   ", "claude-sonnet-5", 1));

        assertThatThrownBy(() -> facade.requestHint(submissionId, callerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_GENERATION_FAILED);

        verify(aiCallHistoryRepository).save(argThatFailed());
        verify(hintRepository, never()).save(any());
    }

    @Test
    void 동시_요청으로_세션이_이미_생성됐으면_그_세션을_다시_조회해서_사용한다() {
        when(judgeServicePort.getSubmission(submissionId)).thenReturn(wrongSubmission(callerId, 1));
        CoachingSession racedSession = persistedSession();
        when(coachingSessionRepository.findInProgressByUserIdAndProblemId(callerId, problemId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedSession));
        when(coachingSessionRepository.save(any()))
                .thenThrow(new BusinessException(ErrorCode.COACHING_SESSION_ALREADY_EXISTS));
        when(hintRepository.findByCoachingSessionId(racedSession.getId())).thenReturn(List.of());
        when(aiModelPort.generate(any(), any())).thenReturn(new AiModelResponse("힌트", "claude-sonnet-5", 1));
        when(hintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HintGenerationFacade.HintGenerationResult result = facade.requestHint(submissionId, callerId);

        assertThat(result.coachingSessionId()).isEqualTo(racedSession.getId());
    }
}
