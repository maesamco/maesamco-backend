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
                followUpAnswerPersistenceService.completeWithAnswer(
                        session.getId(), followUpQuestionId, answerText, session.getSubmissionId(), session.getProblemId()
                );

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

    public record FollowUpAnswerRegisterResult(FollowUpAnswer followUpAnswer, CoachingSessionStatus coachingSessionStatus) {
    }
}
