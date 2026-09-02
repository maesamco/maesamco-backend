package com.maesamco.coaching.application.query_service;

import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.Hint;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.HintRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 힌트 목록 조회(코칭 서비스 API 명세 2번 API) — Judge Service 조회(소유권 확인) 하나뿐이고
 * 자기 DB 쓰기가 없어 Facade가 아니라 QueryService로 둔다(팀 컨벤션 2절).
 *
 * TODO: 지금은 (user_id, problem_id)의 진행 중인 세션 기준으로만 힌트를 조회한다 — 이미
 * COMPLETED된 회차의 과거 힌트 히스토리는 조회되지 않는다(2026-09-02, 코칭 세션을 문제당
 * 재시도 묶음으로 바꾸면서 생긴 범위 제한). 완료된 회차의 히스토리 조회가 필요해지면,
 * 어떤 submissionId가 어떤 회차(세션)에 속했는지 추적할 방법을 별도로 설계해야 한다.
 *
 * ✅ 2026-09-02(PR #70 리뷰, 용현님 P2): 위 TODO 때문에, 예전에 COMPLETED된 회차의
 * submissionId로 조회하면 그 사이 새로 시작된 IN_PROGRESS 세션(같은 문제의 다른 회차)의
 * 힌트가 엉뚱하게 반환되는 문제가 있었다. session.getSubmissionId()(항상 그 세션이 다루는
 * 최신 제출로 갈아탐, HintGenerationFacade.findOrCreateSession() 참고)와 요청받은
 * submissionId가 일치하는지 확인해서, 다른 회차의 제출이면 빈 목록을 반환하도록 막았다 —
 * "아직 힌트를 요청한 적 없음"과 동일하게 처리(에러 아님, 위 TODO가 풀리기 전까지 과거
 * 히스토리는 여전히 조회 불가하다는 한계는 남아있음).
 */
@Service
public class HintQueryService {

    private final JudgeServicePort judgeServicePort;
    private final CoachingSessionRepository coachingSessionRepository;
    private final HintRepository hintRepository;

    public HintQueryService(
            JudgeServicePort judgeServicePort,
            CoachingSessionRepository coachingSessionRepository,
            HintRepository hintRepository
    ) {
        this.judgeServicePort = judgeServicePort;
        this.coachingSessionRepository = coachingSessionRepository;
        this.hintRepository = hintRepository;
    }

    public List<Hint> getHints(UUID submissionId, UUID callerId) {
        SubmissionSnapshot submission = judgeServicePort.getSubmission(submissionId);
        if (!submission.userId().equals(callerId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        return coachingSessionRepository.findInProgressByUserIdAndProblemId(submission.userId(), submission.problemId())
                .filter(session -> session.getSubmissionId().equals(submissionId))
                .map(session -> hintRepository.findByCoachingSessionId(session.getId()))
                .orElseGet(List::of);
    }
}
