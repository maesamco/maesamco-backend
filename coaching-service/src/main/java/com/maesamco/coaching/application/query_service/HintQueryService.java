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

        return coachingSessionRepository.findBySubmissionId(submissionId)
                .map(session -> hintRepository.findByCoachingSessionId(session.getId()))
                .orElseGet(List::of);
    }
}
