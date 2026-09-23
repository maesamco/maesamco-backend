package com.maesamco.content.application.dailyquiz.query_service;

import com.maesamco.content.application.dailyquiz.port.ConceptLookupPort;
import com.maesamco.content.application.dailyquiz.port.ProblemProgressConceptPort;
import com.maesamco.content.application.dailyquiz.port.UserInterestConceptPort;
import com.maesamco.content.application.dailyquiz.query.DailyQuizConceptCandidatesGetQuery;
import com.maesamco.content.domain.dailyquiz.DailyQuizConceptCandidates;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
// import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 사용자 풀이 이력 또는 관심 개념을 이용해 Daily Quiz 출제 개념 후보를 조회합니다.
 *
 * TODO: ProblemProgressConceptPort의 실제 구현이 병합되면
 * Service로 등록합니다.
 */
// @Service
@RequiredArgsConstructor
public class DailyQuizConceptCandidateQueryService {

    private final ProblemProgressConceptPort problemProgressConceptPort;

    private final UserInterestConceptPort userInterestConceptPort;

    private final ConceptLookupPort conceptLookupPort;

    // 배치 실행 날짜와 동일한 timezone으로 CORRECT 조회 cutoff를 계산합니다.
    private final Clock dailyQuizClock;

    public DailyQuizConceptCandidates get(DailyQuizConceptCandidatesGetQuery query) {
        if (query == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "개념 후보 조회 조건은 필수입니다."
            );
        }
        boolean hasProblemProgress = problemProgressConceptPort.existsByUserId(query.userId());

        // 풀이 이력이 없으면 관심 개념 ID를 조회하고 Daily Quiz 개념 태그로 변환합니다.
        if (!hasProblemProgress) {
            List<UUID> interestConceptIds = userInterestConceptPort.getInterestConceptIds(query.userId());
            List<String> conceptTags = conceptLookupPort.getConceptTags(interestConceptIds);

            return DailyQuizConceptCandidates.fromInterests(conceptTags);
        }

        // 풀이 이력이 있으면 WRONG 개념을 조회합니다.
        List<String> wrongConcepts = problemProgressConceptPort.getWrongConceptTags(query.userId());

        // 퀴즈 날짜의 시작 시각을 구하고, 그 전에 CORRECT 처리된 개념을 조회합니다.
        Instant quizDateStart = query.attemptDate()
                .atStartOfDay(dailyQuizClock.getZone())
                .toInstant();

        List<String> correctConcepts = problemProgressConceptPort.getCorrectConceptTagsBefore(
                query.userId(),
                quizDateStart
        );

        return DailyQuizConceptCandidates.fromProblemProgress(wrongConcepts, correctConcepts);
    }
}
