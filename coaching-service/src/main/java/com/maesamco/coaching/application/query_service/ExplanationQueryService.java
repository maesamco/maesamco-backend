package com.maesamco.coaching.application.query_service;

import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.Explanation;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import com.maesamco.coaching.domain.entity.FollowUpQuestion;
import com.maesamco.coaching.domain.repository.ExplanationRepository;
import com.maesamco.coaching.domain.repository.FollowUpAnswerRepository;
import com.maesamco.coaching.domain.repository.FollowUpQuestionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 설명·역질문 조회(코칭 서비스 API 명세 4번 API) — Judge Service 조회(소유권 확인) 하나뿐이고
 * 자기 DB 쓰기가 없어 Facade가 아니라 QueryService로 둔다(팀 컨벤션 2절).
 *
 * 이슈 #84 결정 1·2(V5·V6)로 Explanation의 유일성 기준이 submission_id가 되면서, 이
 * 조회는 요청받은 submissionId로 바로 findBySubmissionId()하면 된다 — HintQueryService가
 * 세션-제출 스코프 불일치를 가드해야 했던 것과 달리 세션 경유 조회 자체가 필요 없다.
 */
@Service
public class ExplanationQueryService {

    private final JudgeServicePort judgeServicePort;
    private final ExplanationRepository explanationRepository;
    private final FollowUpQuestionRepository followUpQuestionRepository;
    private final FollowUpAnswerRepository followUpAnswerRepository;

    public ExplanationQueryService(
            JudgeServicePort judgeServicePort,
            ExplanationRepository explanationRepository,
            FollowUpQuestionRepository followUpQuestionRepository,
            FollowUpAnswerRepository followUpAnswerRepository
    ) {
        this.judgeServicePort = judgeServicePort;
        this.explanationRepository = explanationRepository;
        this.followUpQuestionRepository = followUpQuestionRepository;
        this.followUpAnswerRepository = followUpAnswerRepository;
    }

    public ExplanationQueryResult getExplanation(UUID submissionId, UUID callerId) {
        SubmissionSnapshot submission = judgeServicePort.getSubmission(submissionId);
        if (!submission.userId().equals(callerId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        Explanation explanation = explanationRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPLANATION_NOT_FOUND));

        FollowUpQuestion followUpQuestion =
                followUpQuestionRepository.findByExplanationId(explanation.getId()).orElse(null);
        FollowUpAnswer followUpAnswer = followUpQuestion == null
                ? null
                : followUpAnswerRepository.findByFollowUpQuestionId(followUpQuestion.getId()).orElse(null);

        return new ExplanationQueryResult(explanation, followUpQuestion, followUpAnswer);
    }

    public record ExplanationQueryResult(
            Explanation explanation, FollowUpQuestion followUpQuestion, FollowUpAnswer followUpAnswer
    ) {
    }
}
