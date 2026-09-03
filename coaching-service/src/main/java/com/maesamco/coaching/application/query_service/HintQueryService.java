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
 * ✅ 2026-09-03(이슈 #84, V5): 문제당 세션이 평생 최대 1개로 바뀌면서, "COMPLETED된 회차의
 * 과거 힌트 히스토리는 조회되지 않는다"는 예전 TODO가 해소됐다 — 세션이 상태와 무관하게
 * 항상 유일하므로, COMPLETED 여부와 관계없이 같은 세션의 힌트를 그대로 조회할 수 있다.
 *
 * ✅ 2026-09-02(PR #70 리뷰, 용현님 P2): V4 시절엔 COMPLETED된 회차의 submissionId로
 * 조회하면 그 사이 새로 시작된 다른 회차(세션)의 힌트가 엉뚱하게 반환되는 문제가 있었다.
 * session.getSubmissionId()(항상 그 세션이 다루는 최신 제출로 갈아탐,
 * HintGenerationFacade.findOrCreateSession() 참고)와 요청받은 submissionId가 일치하는지
 * 확인해서, 세션이 이미 더 최신 제출로 넘어갔다면 빈 목록을 반환하도록 막았다 — "아직 힌트를
 * 요청한 적 없음"과 동일하게 처리(에러 아님). V5로 세션이 유일해진 뒤에도 이 가드는
 * 그대로 유효하다(다른 회차가 아니라, 같은 세션 내에서 더 예전 제출 ID로 조회하는 경우를
 * 계속 막아준다).
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

        return coachingSessionRepository.findByUserIdAndProblemId(submission.userId(), submission.problemId())
                .filter(session -> session.getSubmissionId().equals(submissionId))
                .map(session -> hintRepository.findByCoachingSessionId(session.getId()))
                .orElseGet(List::of);
    }
}
