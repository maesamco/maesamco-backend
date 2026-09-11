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
 * ⚠️ 2026-09-11(이슈 #165) 정정: 위 PR #70 가드(session.getSubmissionId()와 요청받은
 * submissionId가 일치해야만 반환)를 걷어냈다. V4 시절엔 "완료 후 재도전하면 새 세션이
 * 열려서, 오래된 submissionId로 조회하면 다른 회차(세션)의 힌트가 엉뚱하게 보일 수 있다"는
 * 문제를 막기 위한 가드였는데, V5로 문제당 세션이 평생 최대 1개로 확정되면서 애초에 "다른
 * 회차의 세션"이 구조적으로 존재할 수 없게 됐다 — findByUserIdAndProblemId()는 이제 항상
 * 유일한 세션 하나만 반환하므로 이 가드가 막던 상황 자체가 발생 불가능해졌다.
 *
 * 반면 부작용은 실제로 발생했다 — 진행 중 재도전(완료 여부 무관)마다 세션의 submissionId가
 * 갈아타므로, 그 이전 제출 ID로는 더 이상 힌트를 조회할 수 없어 "힌트가 사라졌다"는 혼란을
 * 낳았다(멀티탭/멀티기기에서도 재현). 힌트는 제출 하나가 아니라 문제를 풀어가는 과정 전체에
 * 누적되는 데이터이므로, 어떤 submissionId로 조회하든 그 submissionId가 실제로 속한
 * (userId, problemId)의 세션 전체 힌트를 그대로 반환하는 게 맞다. 소유권(getSubmission()이
 * 조회한 실제 소유자와 callerId 비교)과 문제 범위(요청받은 submissionId가 실제로 속한
 * problemId 사용, 클라이언트가 직접 지정 불가)는 이 가드와 무관하게 그대로 유지되므로
 * 크로스 유저·크로스 문제 유출 위험은 없다.
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
                .map(session -> hintRepository.findByCoachingSessionId(session.getId()))
                .orElseGet(List::of);
    }
}
