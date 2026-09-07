package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.port.AiFeedbackRetryLockPort;
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
 *
 * 재검증(PR #111, 외부 AI 리뷰) — 재시도 횟수 체크~LLM 호출~이력 저장 구간을
 * AiFeedbackRetryLockPort(Redis, HintGenerationFacade의 힌트 생성 락과 동일한 패턴)로
 * 세션 단위 직렬화한다. 동시 요청은 락을 못 얻으면 대기하지 않고 바로
 * AI_FEEDBACK_RETRY_IN_PROGRESS(409)로 응답한다 — 힌트 생성과 달리 이 구간은 Judge Feign
 * 타임아웃 미설정 + LLM 재시도까지 겹치면 최악의 경우 약 2분이 걸릴 수 있어, HTTP 요청
 * 스레드를 그만큼 붙잡아두는 대기 방식은 적절하지 않다고 판단했다.
 */
@Component
public class AiFeedbackRetryFacade {

    private static final int MAX_RETRY_COUNT = 3;
    // AiFeedbackQueryService가 "재시도 예산 소진" 상태를 판별할 때도 이 상수를 그대로
    // 참조한다(PR #111 재검증, GET 응답 상태 세분화) — 재시도 상한의 원본 정의는 이
    // 클래스라 여기서만 값을 관리한다.
    public static final long MAX_ATTEMPTS = MAX_RETRY_COUNT + 1L;

    private final CoachingSessionRepository coachingSessionRepository;
    private final AiFeedbackRepository aiFeedbackRepository;
    private final AiCallHistoryRepository aiCallHistoryRepository;
    private final ExplanationRepository explanationRepository;
    private final FollowUpQuestionRepository followUpQuestionRepository;
    private final FollowUpAnswerRepository followUpAnswerRepository;
    private final FeedbackGenerationFacade feedbackGenerationFacade;
    private final AiFeedbackRetryLockPort aiFeedbackRetryLockPort;

    public AiFeedbackRetryFacade(
            CoachingSessionRepository coachingSessionRepository,
            AiFeedbackRepository aiFeedbackRepository,
            AiCallHistoryRepository aiCallHistoryRepository,
            ExplanationRepository explanationRepository,
            FollowUpQuestionRepository followUpQuestionRepository,
            FollowUpAnswerRepository followUpAnswerRepository,
            FeedbackGenerationFacade feedbackGenerationFacade,
            AiFeedbackRetryLockPort aiFeedbackRetryLockPort
    ) {
        this.coachingSessionRepository = coachingSessionRepository;
        this.aiFeedbackRepository = aiFeedbackRepository;
        this.aiCallHistoryRepository = aiCallHistoryRepository;
        this.explanationRepository = explanationRepository;
        this.followUpQuestionRepository = followUpQuestionRepository;
        this.followUpAnswerRepository = followUpAnswerRepository;
        this.feedbackGenerationFacade = feedbackGenerationFacade;
        this.aiFeedbackRetryLockPort = aiFeedbackRetryLockPort;
    }

    public AiFeedback retryFeedback(UUID submissionId, UUID callerId) {
        CoachingSession session = coachingSessionRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));
        if (!session.getUserId().equals(callerId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        // 자가 리뷰(P1) — 세션이 아직 완료 전이면 completedAt이 null이라
        // findCompletionExplanation()의 Duration.between()에서 NPE가 난다. 완료 전이면
        // 애초에 피드백 생성 시도 자체가 없었던 상태이므로 AI_FEEDBACK_NOT_FOUND(404)로
        // 응답한다 — 새 ErrorCode를 만들 필요 없이 "아직 생성된 피드백이 없다"는 의미가
        // 그대로 맞는다.
        if (!session.isCompleted()) {
            throw new BusinessException(ErrorCode.AI_FEEDBACK_NOT_FOUND);
        }

        // 재검증(PR #111, 외부 AI 리뷰) — 재시도 횟수 체크부터 LLM 호출·이력 저장까지
        // 락 없는 check-then-act였다. 이 구간 전체(HintGenerationFacade와 동일한 이유로
        // DB 트랜잭션이 아니라 Redis 락)를 세션 단위로 직렬화해서, 동시/스크립트성 요청이
        // 전부 같은 스테일 카운트를 읽고 통과하는 걸 막는다.
        String lockToken = UUID.randomUUID().toString();
        if (!aiFeedbackRetryLockPort.tryLock(session.getId(), lockToken)) {
            throw new BusinessException(ErrorCode.AI_FEEDBACK_RETRY_IN_PROGRESS);
        }
        try {
            return doRetryFeedback(session);
        } finally {
            aiFeedbackRetryLockPort.unlock(session.getId(), lockToken);
        }
    }

    private AiFeedback doRetryFeedback(CoachingSession session) {
        if (aiFeedbackRepository.findByCoachingSessionId(session.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.AI_FEEDBACK_ALREADY_EXISTS);
        }

        long attemptCount = aiCallHistoryRepository.countRealAttemptsByCoachingSessionIdAndPurpose(
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
     * session.getCompletedAt() 이전에 생긴 것 중 가장 가까운 것을 고른다. 정상 케이스는
     * 정확히 1개뿐이라 이 로직이 실제로 여러 후보를 비교할 일은 드물지만, 재시도 시점에
     * 사용자가 이미 같은 문제를 재도전해 새 Explanation이 하나 더 생겼을 가능성까지
     * 방어한다.
     *
     * 용현님 리뷰(P1) — 완료 시점 **이후**에 생긴 Explanation(완료 후 재도전)은 먼저
     * 걸러낸다. 예전엔 절대값 거리(Duration.abs())로만 비교해서, 완료 30초 전 Explanation과
     * 완료 5초 후 Explanation 중 후자를 "더 가깝다"고 잘못 고를 수 있었다 — 완료 이후에
     * 생긴 Explanation은 그 완료를 만들어낸 답변일 수가 없으므로 애초에 후보에서 제외해야
     * 한다.
     */
    private Explanation findCompletionExplanation(CoachingSession session) {
        List<Explanation> candidates = explanationRepository.findByCoachingSessionId(session.getId());

        return candidates.stream()
                .filter(candidate -> !candidate.getCreatedAt().isAfter(session.getCompletedAt()))
                .min(Comparator.comparing(candidate ->
                        Duration.between(candidate.getCreatedAt(), session.getCompletedAt())
                ))
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_FEEDBACK_NOT_FOUND));
    }
}
