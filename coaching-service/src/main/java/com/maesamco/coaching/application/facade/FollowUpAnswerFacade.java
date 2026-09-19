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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 역질문 답변 등록(코칭 서비스 API 명세 5번 API, 이슈 #51) — 컨트롤러의 진입점. 소유권
 * 검증(읽기 전용) → FollowUpAnswerPersistenceService의 원자적 DB 트랜잭션(답변 저장 + 세션
 * 완료 + Outbox 기록) → 성공하면 FeedbackGenerationFacade를 best-effort로 호출하는 순서로
 * 조율한다(팀 컨벤션 2절 — 이 클래스 자체엔 @Transactional을 걸지 않는다).
 */
@Slf4j
@Component
public class FollowUpAnswerFacade {

    private final FollowUpQuestionRepository followUpQuestionRepository;
    private final ExplanationRepository explanationRepository;
    private final CoachingSessionRepository coachingSessionRepository;
    private final FollowUpAnswerPersistenceService followUpAnswerPersistenceService;
    private final FeedbackGenerationFacade feedbackGenerationFacade;

    public FollowUpAnswerFacade(
            FollowUpQuestionRepository followUpQuestionRepository,
            ExplanationRepository explanationRepository,
            CoachingSessionRepository coachingSessionRepository,
            FollowUpAnswerPersistenceService followUpAnswerPersistenceService,
            FeedbackGenerationFacade feedbackGenerationFacade
    ) {
        this.followUpQuestionRepository = followUpQuestionRepository;
        this.explanationRepository = explanationRepository;
        this.coachingSessionRepository = coachingSessionRepository;
        this.followUpAnswerPersistenceService = followUpAnswerPersistenceService;
        this.feedbackGenerationFacade = feedbackGenerationFacade;
    }

    /**
     * 요청에 노출되는 식별자는 followUpQuestionId뿐이라, 소유권 검증 체인(역질문 →
     * 설명 → 코칭 세션) 중 어디서 실패하든 전부 FOLLOW_UP_QUESTION_NOT_FOUND(404)로
     * 응답한다 — 팀 컨벤션 12절(다른 사용자의 리소스는 존재하지 않는 리소스와 동일하게
     * 404로 응답, 별도 코드를 두지 않는다).
     */
    public FollowUpAnswerRegisterResult registerAnswer(UUID followUpQuestionId, String answerText, UUID callerId) {
        FollowUpQuestion followUpQuestion = followUpQuestionRepository.findById(followUpQuestionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_UP_QUESTION_NOT_FOUND));
        Explanation explanation = explanationRepository.findById(followUpQuestion.getExplanationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_UP_QUESTION_NOT_FOUND));
        CoachingSession session = coachingSessionRepository.findById(explanation.getCoachingSessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_UP_QUESTION_NOT_FOUND));

        if (!session.getUserId().equals(callerId)) {
            throw new BusinessException(ErrorCode.FOLLOW_UP_QUESTION_NOT_FOUND);
        }

        FollowUpAnswerPersistenceService.FollowUpAnswerCompletionResult completion =
                completeWithAnswer(session.getId(), followUpQuestionId, answerText);

        try {
            feedbackGenerationFacade.generateFeedback(
                    completion.coachingSession(), explanation, followUpQuestion, completion.followUpAnswer()
            );
        } catch (RuntimeException e) {
            log.warn("AI 종합 피드백 생성 호출 실패 - coachingSessionId={}", session.getId(), e);
        }

        return new FollowUpAnswerRegisterResult(
                completion.followUpAnswer(), completion.coachingSession().getStatus()
        );
    }

    /**
     * 이슈 #218(V15) — CoachingSession에 @Version이 도입된 뒤로,
     * completeWithAnswer()가 트랜잭션 안에서 하는 CoachingSession.complete()+save()가
     * 서로 다른 역질문을 거의 동시에 완료 처리하려는 경합에서
     * ObjectOptimisticLockingFailureException으로 실패할 수 있다. saveAndFlush()로
     * 즉시 flush되므로 이 예외는 completeWithAnswer() 트랜잭션이 커밋되기 전에 나서,
     * Spring이 그 트랜잭션 전체(방금 저장한 FollowUpAnswer 포함)를 롤백한다 — 부분
     * 성공 없이 처음부터 다시 시도해도 안전하다.
     *
     * user-service의 ChangePasswordRetryService와 동일한 패턴으로 completeWithAnswer()
     * 전체를 한 번만 재시도한다. 재시도로 세션을 다시 조회하면 대개 경합 상대가 이미
     * COMPLETED로 완료해둔 뒤라, CoachingSession.complete()는
     * completeSessionIfNeeded()의 isCompleted() 가드에 걸려 재호출 자체가 생략되고
     * 성공으로 끝난다. 재시도까지 충돌하면 COACHING_SESSION_UPDATE_CONFLICT로 변환한다.
     */
    private FollowUpAnswerPersistenceService.FollowUpAnswerCompletionResult completeWithAnswer(
            UUID coachingSessionId, UUID followUpQuestionId, String answerText
    ) {
        try {
            return followUpAnswerPersistenceService.completeWithAnswer(coachingSessionId, followUpQuestionId, answerText);
        } catch (OptimisticLockingFailureException firstException) {
            return retryCompleteWithAnswer(coachingSessionId, followUpQuestionId, answerText);
        }
    }

    private FollowUpAnswerPersistenceService.FollowUpAnswerCompletionResult retryCompleteWithAnswer(
            UUID coachingSessionId, UUID followUpQuestionId, String answerText
    ) {
        try {
            return followUpAnswerPersistenceService.completeWithAnswer(coachingSessionId, followUpQuestionId, answerText);
        } catch (OptimisticLockingFailureException secondException) {
            throw new BusinessException(ErrorCode.COACHING_SESSION_UPDATE_CONFLICT);
        }
    }

    public record FollowUpAnswerRegisterResult(FollowUpAnswer followUpAnswer, CoachingSessionStatus coachingSessionStatus) {
    }
}
