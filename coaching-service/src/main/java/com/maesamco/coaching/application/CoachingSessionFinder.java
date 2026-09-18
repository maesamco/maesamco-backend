package com.maesamco.coaching.application;

import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 문제당 세션은 상태와 무관하게 평생 최대 1개다(2026-09-03 재검토, 이슈 #84 — V4의
 * "IN_PROGRESS인 세션만 이어서 쓰고 COMPLETED 후 재도전 시 새 세션"에서 변경). 오답 힌트
 * 요청(HintGenerationFacade)과 60초 설명 등록(ExplanationGenerationFacade) 양쪽에서
 * 완전히 동일한 find-or-create 로직이 필요해서 공용으로 뺐다 — 두 Facade가 각자 이 로직을
 * 들고 있으면 나중에 한쪽만 고치고 다른 쪽을 놓치는 드리프트가 생기기 쉽다.
 */
@Component
public class CoachingSessionFinder {

    private final CoachingSessionRepository coachingSessionRepository;

    public CoachingSessionFinder(CoachingSessionRepository coachingSessionRepository) {
        this.coachingSessionRepository = coachingSessionRepository;
    }

    /**
     * 이미 세션이 있으면 재사용(재시도마다 최신 제출로 submission_id를 갈아탐), 없으면
     * 새로 만든다. 동시 요청으로 두 트랜잭션이 동시에 "없다"고 판단해 둘 다 생성을
     * 시도하면, 나중에 flush되는 쪽이 UNIQUE(user_id, problem_id) 위반으로
     * COACHING_SESSION_ALREADY_EXISTS를 받는다 — 이 경우 방금 다른 트랜잭션이 만든 세션을
     * 재조회해서 쓴다(힌트 요청·설명 등록 자체를 실패시킬 이유가 없다, PR #70 리뷰와
     * 동일한 판단).
     *
     * PR #228 리뷰(용현님 P1) — 재조회한 세션을 그대로 반환하면, 이 생성 경합에서 진 쪽의
     * attemptNo가 유실될 수 있다(예: attemptNo=1이 먼저 INSERT에 성공하고 attemptNo=2는
     * UNIQUE 충돌 후 재조회만 하면, 최종 DB엔 attemptNo=1 상태가 남는다). "이미 있는
     * 세션" 분기와 동일하게 advanceAndSave()에 태워서, advanceToSubmission()의 역행 방지가
     * 최신 attempt만 실제로 반영되도록 한다.
     *
     * PR #88 리뷰(용현님 P1) — submission_id를 갈아탈지는 더 이상 여기서 "값이 다른가"로
     * 판단하지 않는다. CoachingSession.advanceToSubmission()이 attemptNo 기준으로
     * 자체 방어하므로, 과거 제출로 들어온 요청은 advanceToSubmission()이 false를 반환해
     * save() 자체가 일어나지 않는다.
     */
    /**
     * PR #164 리뷰(용현님 P1) — 완료된 세션에 재도전 오답이 들어오면, findOrCreate()가
     * 거부 전에 이미 advanceToSubmission()+save()로 submission_id를 갈아태워 버린다.
     * 완료 여부만 먼저 읽고 싶은 호출자를 위해 mutation 없는 순수 조회를 별도로 둔다.
     */
    public Optional<CoachingSession> find(UUID userId, UUID problemId) {
        return coachingSessionRepository.findByUserIdAndProblemId(userId, problemId);
    }

    public CoachingSession findOrCreate(SubmissionSnapshot submission) {
        return coachingSessionRepository.findByUserIdAndProblemId(submission.userId(), submission.problemId())
                .map(session -> advanceAndSave(session, submission))
                .orElseGet(() -> {
                    try {
                        return coachingSessionRepository.save(
                                CoachingSession.create(
                                        submission.submissionId(), submission.userId(), submission.problemId(),
                                        submission.attemptNo()
                                )
                        );
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.COACHING_SESSION_ALREADY_EXISTS) {
                            CoachingSession existingSession = coachingSessionRepository
                                    .findByUserIdAndProblemId(submission.userId(), submission.problemId())
                                    .orElseThrow(() -> e);
                            return advanceAndSave(existingSession, submission);
                        }
                        throw e;
                    }
                });
    }

    /**
     * 이슈 #218(V15) — CoachingSession에 @Version이 도입된 뒤로, 이 save()는
     * 서로 다른 힌트/설명 요청이 같은 세션의 submission_id를 거의 동시에 갈아태우면
     * ObjectOptimisticLockingFailureException을 던질 수 있다(그 전까지는 나중에 flush된
     * 쪽이 먼저 flush된 attemptNo 증가분을 조용히 덮어쓰는 lost-update였음).
     * user-service의 ChangePasswordRetryService와 동일한 패턴으로 한 번만 재시도하고,
     * 재시도한 save()마저 충돌하면 COACHING_SESSION_UPDATE_CONFLICT로 변환한다 —
     * 세션 자체는 이미 존재가 확인된 상태라 재조회 실패(orElseThrow)는 이론상 발생하지
     * 않지만, 이 메서드가 findOrCreate()의 "이미 있는 세션" 분기에서만 호출되므로
     * 방어적으로 COACHING_SESSION_NOT_FOUND로 처리한다.
     */
    private CoachingSession advanceAndSave(CoachingSession session, SubmissionSnapshot submission) {
        if (!session.advanceToSubmission(submission.submissionId(), submission.attemptNo())) {
            return session;
        }
        try {
            return coachingSessionRepository.save(session);
        } catch (OptimisticLockingFailureException firstException) {
            return retryAdvanceAndSave(submission);
        }
    }

    private CoachingSession retryAdvanceAndSave(SubmissionSnapshot submission) {
        CoachingSession freshSession = coachingSessionRepository
                .findByUserIdAndProblemId(submission.userId(), submission.problemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COACHING_SESSION_NOT_FOUND));
        if (!freshSession.advanceToSubmission(submission.submissionId(), submission.attemptNo())) {
            return freshSession;
        }
        try {
            return coachingSessionRepository.save(freshSession);
        } catch (OptimisticLockingFailureException secondException) {
            throw new BusinessException(ErrorCode.COACHING_SESSION_UPDATE_CONFLICT);
        }
    }
}
