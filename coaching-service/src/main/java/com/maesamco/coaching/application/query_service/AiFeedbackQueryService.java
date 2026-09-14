package com.maesamco.coaching.application.query_service;

import com.maesamco.coaching.application.facade.AiFeedbackRetryFacade;
import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.AiCallPurpose;
import com.maesamco.coaching.domain.entity.AiFeedback;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.AiFeedbackRepository;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * AI 종합 피드백 조회(코칭 서비스 API 명세 6번 API, 이슈 #52) — 외부 호출 없이 DB 조회만
 * 하므로 Facade가 아니라 QueryService로 둔다(팀 컨벤션 2절).
 *
 * PR #164 리뷰(용현님 P1) 대응 — 원래는 Judge Service 호출 없이 CoachingSession의
 * submission_id로 직접 세션을 찾았다("Judge Service 호출을 하나 줄이는 정당한 단순화").
 * 하지만 submission_id는 재도전마다 갈아타는 가변 필드라(이슈 #84), 재도전으로 그 값이
 * 바뀐 뒤에는 예전 submissionId로 더 이상 같은 세션(과 피드백)을 찾을 수 없었다 — 완전히
 * 같은 메커니즘의 버그가 HintQueryService에서 실제로 재현된 적이 있다(이슈 #165, 멀티탭/
 * 멀티기기에서 "힌트가 사라졌다"는 혼란). 피드백도 힌트와 마찬가지로 특정 제출 1건이
 * 아니라 세션 전체에 귀속되는 데이터이므로, HintQueryService와 동일하게 Judge Service로
 * 조회한 (userId, problemId) 기준으로 세션을 찾도록 통일한다.
 */
@Service
public class AiFeedbackQueryService {

    private final JudgeServicePort judgeServicePort;
    private final CoachingSessionRepository coachingSessionRepository;
    private final AiFeedbackRepository aiFeedbackRepository;
    private final AiCallHistoryRepository aiCallHistoryRepository;

    public AiFeedbackQueryService(
            JudgeServicePort judgeServicePort,
            CoachingSessionRepository coachingSessionRepository,
            AiFeedbackRepository aiFeedbackRepository,
            AiCallHistoryRepository aiCallHistoryRepository
    ) {
        this.judgeServicePort = judgeServicePort;
        this.coachingSessionRepository = coachingSessionRepository;
        this.aiFeedbackRepository = aiFeedbackRepository;
        this.aiCallHistoryRepository = aiCallHistoryRepository;
    }

    /**
     * 재검증(PR #111, 외부 AI 리뷰) — "세션 미완료"/"완료됐지만 아직 없음"/"재시도 예산
     * 소진"을 전부 같은 AI_FEEDBACK_NOT_FOUND(404)로 응답하면, 클라이언트가 폴링을
     * 계속할지 재시도 버튼을 보여줄지 구분할 방법이 없었다. 세션 완료 여부와 재시도 예산
     * 소진 여부는 클라이언트 판단에 바로 쓸 수 있는 정보라 별도 코드로 분리한다 — "생성
     * 진행 중"과 "실패했지만 재시도 가능"의 구분은 PENDING 이력 마커가 있어야 가능해서
     * (지금은 성공/실패가 끝난 뒤에만 이력을 남김) 이번엔 손대지 않았다.
     */
    @Transactional(readOnly = true)
    public AiFeedback getFeedback(UUID submissionId, UUID callerId) {
        CoachingSession session = findOwnedSession(submissionId, callerId);

        if (!session.isCompleted()) {
            throw new BusinessException(ErrorCode.AI_FEEDBACK_NOT_STARTED);
        }

        return aiFeedbackRepository.findByCoachingSessionId(session.getId())
                .orElseThrow(() -> {
                    long attemptCount = aiCallHistoryRepository.countRealAttemptsByCoachingSessionIdAndPurpose(
                            session.getId(), AiCallPurpose.FEEDBACK
                    );
                    return attemptCount >= AiFeedbackRetryFacade.MAX_ATTEMPTS
                            ? new BusinessException(ErrorCode.AI_FEEDBACK_RETRY_LIMIT_EXCEEDED)
                            : new BusinessException(ErrorCode.AI_FEEDBACK_NOT_FOUND);
                });
    }

    /**
     * 팀 컨벤션 12절 — 다른 사용자의 리소스는 존재하지 않는 리소스와 동일하게 404로 응답한다.
     * AiFeedbackRetryFacade도 동일한 체크가 필요하지만, 컨트롤러의 requireAuthenticated()처럼
     * 클래스마다 자체적으로 갖는 관례를 따라 별도로 중복 구현한다(둘을 하나로 묶으면 서로
     * 다른 계층 성격의 클래스가 얽히게 된다).
     *
     * HintQueryService.getHints()와 동일한 이유로 CoachingSession.getSubmissionId()가
     * 아니라 Judge Service가 돌려준 (userId, problemId)로 세션을 찾는다 — 소유권도 세션에
     * 저장된 값이 아니라 Judge Service가 확인한 실제 제출 소유자로 검증한다.
     */
    private CoachingSession findOwnedSession(UUID submissionId, UUID callerId) {
        SubmissionSnapshot submission = judgeServicePort.getSubmission(submissionId);

        if (!submission.userId().equals(callerId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        return coachingSessionRepository.findByUserIdAndProblemId(submission.userId(), submission.problemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));
    }
}
