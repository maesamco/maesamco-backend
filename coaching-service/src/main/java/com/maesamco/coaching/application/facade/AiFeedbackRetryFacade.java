package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.domain.entity.AiCallPurpose;
import com.maesamco.coaching.domain.entity.AiFeedback;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.AiFeedbackRepository;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.ExplanationRepository;
import com.maesamco.coaching.domain.repository.FollowUpAnswerRepository;
import com.maesamco.coaching.domain.repository.FollowUpQuestionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * AI 종합 피드백 재시도(코칭 서비스 API 명세 6번 API, 이슈 #52) — 역질문 답변 등록(#51)
 * 시점의 best-effort 생성이 실패했을 때, 사용자가 직접 트리거해서 다시 시도한다.
 *
 * 원래는 백그라운드 스케줄러(주기적 스캔)로 설계했으나, 이 코드베이스에 @Scheduled
 * 선례가 전혀 없어 설계 부담이 크고("Judge Service Outbox 릴레이 워커와 같은 패턴"이라는
 * 이슈 본문의 언급도 실제로는 존재하지 않는 컴포넌트를 가리킨다 — judge-service엔 아직
 * presentation 계층 자체가 없음) 최대 5분을 기다려야 하는 UX도 좋지 않아, 사용자 트리거
 * 재시도 엔드포인트로 대체했다. 남용 방어는 이 프로젝트에 이미 확립된 패턴대로 Gateway
 * RateLimitFilter에 맡긴다 — 이 경로(/api/v1/coaching/submissions/**)는 기존
 * "LLM 호출 비용 있는 액션" 룰에 prefix로 이미 걸려 있어 별도 룰이 필요 없다.
 *
 * AiCallHistory가 explanationId/followUpAnswerId를 저장하지 않아, 재시도 대상을
 * 찾으려면 "세션 완료 시점에 실제로 쓰인 그 답변"을 다시 짚어내야 한다 — 같은 세션에서
 * 재도전(재제출)마다 새 Explanation이 생길 수 있어(이슈 #84)
 * CoachingSession.getSubmissionId()(재도전마다 최신 제출로 갈아탐)를 그대로 쓰면 엉뚱한
 * 답변으로 재구성될 수 있기 때문이다. 세션의 모든 Explanation 후보 중
 * session.getCompletedAt()과 가장 가까운 것을 고른다(정상 케이스는 정확히 1개).
 *
 * FeedbackGenerationFacade를 부르므로(외부/LLM 호출 체인) 팀 컨벤션 2절상 Facade
 * 성격이라 이 패키지에 둔다. 실제 트랜잭션은 그 안의 FeedbackPersistenceService가
 * 담당하므로 이 클래스 자체엔 @Transactional을 걸지 않는다.
 */
@Component
public class AiFeedbackRetryFacade {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long MAX_ATTEMPTS = MAX_RETRY_COUNT + 1L;

    private final CoachingSessionRepository coachingSessionRepository;
    private final AiFeedbackRepository aiFeedbackRepository;
    private final AiCallHistoryRepository aiCallHistoryRepository;
    private final ExplanationRepository explanationRepository;
    private final FollowUpQuestionRepository followUpQuestionRepository;
    private final FollowUpAnswerRepository followUpAnswerRepository;
    private final FeedbackGenerationFacade feedbackGenerationFacade;

    public AiFeedbackRetryFacade(
            CoachingSessionRepository coachingSessionRepository,
            AiFeedbackRepository aiFeedbackRepository,
            AiCallHistoryRepository aiCallHistoryRepository,
            ExplanationRepository explanationRepository,
            FollowUpQuestionRepository followUpQuestionRepository,
            FollowUpAnswerRepository followUpAnswerRepository,
            FeedbackGenerationFacade feedbackGenerationFacade
    ) {
        this.coachingSessionRepository = coachingSessionRepository;
        this.aiFeedbackRepository = aiFeedbackRepository;
        this.aiCallHistoryRepository = aiCallHistoryRepository;
        this.explanationRepository = explanationRepository;
        this.followUpQuestionRepository = followUpQuestionRepository;
        this.followUpAnswerRepository = followUpAnswerRepository;
        this.feedbackGenerationFacade = feedbackGenerationFacade;
    }

    public AiFeedback retryFeedback(UUID submissionId, UUID callerId) {
        CoachingSession session = coachingSessionRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));
        if (!session.getUserId().equals(callerId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        if (aiFeedbackRepository.findByCoachingSessionId(session.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.AI_FEEDBACK_ALREADY_EXISTS);
        }

        long attemptCount = aiCallHistoryRepository.countByCoachingSessionIdAndPurpose(
                session.getId(), AiCallPurpose.FEEDBACK
        );
        if (attemptCount >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.AI_FEEDBACK_RETRY_LIMIT_EXCEEDED);
        }

        Explanation explanation = findCompletionExplanation(session);
        FollowUpQuestion followUpQuestion = followUpQuestionRepository.findByExplanationId(explanation.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_FEEDBACK_NOT_FOUND));
        FollowUpAnswer followUpAnswer =
                followUpAnswerRepository.findByFollowUpQuestionId(followUpQuestion.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.AI_FEEDBACK_NOT_FOUND));

        feedbackGenerationFacade.generateFeedback(session, explanation, followUpQuestion, followUpAnswer);

        return aiFeedbackRepository.findByCoachingSessionId(session.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_FEEDBACK_NOT_FOUND));
    }

    /**
     * "완료 시점 답변 기준" 재구성 — 세션의 모든 Explanation 후보 중
     * session.getCompletedAt()과 가장 가까운 것을 고른다. 정상 케이스는 정확히
     * 1개뿐이라 이 로직이 실제로 여러 후보를 비교할 일은 드물지만, 재시도 시점에 사용자가
     * 이미 같은 문제를 재도전해 새 Explanation이 하나 더 생겼을 가능성까지 방어한다.
     */
    private Explanation findCompletionExplanation(CoachingSession session) {
        List<Explanation> candidates = explanationRepository.findByCoachingSessionId(session.getId());

        return candidates.stream()
                .min(Comparator.comparing(candidate ->
                        Duration.between(candidate.getCreatedAt(), session.getCompletedAt()).abs()
                ))
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_FEEDBACK_NOT_FOUND));
    }
}
