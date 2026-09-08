package com.maesamco.content.dailyquiz.application.query_service;

import com.maesamco.content.dailyquiz.application.port.ConceptLookupPort;
import com.maesamco.content.dailyquiz.application.port.ProblemProgressConceptPort;
import com.maesamco.content.dailyquiz.application.port.UserInterestConceptPort;
import com.maesamco.content.dailyquiz.application.query.DailyQuizConceptCandidatesGetQuery;
import com.maesamco.content.dailyquiz.domain.DailyQuizConceptCandidates;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 사용자 풀이 이력 또는 관심 개념을 이용해 Daily Quiz 출제 개념 후보를 조회합니다.
 *
 * TODO: ProblemProgressQueryRepository와 ConceptRepository가 병합되면
 * 임시 Port 주입을 실제 Repository 주입으로 교체하고 @Service로 등록합니다.
 */
@RequiredArgsConstructor
public class DailyQuizConceptCandidateQueryService {

    private static final ZoneId QUIZ_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final ProblemProgressConceptPort problemProgressConceptPort;

    // TODO: ProblemProgressQueryRepository가 병합되면 실제 Repository 주입으로 교체합니다.
    private final UserInterestConceptPort userInterestConceptPort;

    // TODO: ConceptRepository가 병합되면 실제 Repository 주입으로 교체합니다.
    private final ConceptLookupPort conceptLookupPort;

    public DailyQuizConceptCandidates get(DailyQuizConceptCandidatesGetQuery query) {
        if (query == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "개념 후보 조회 조건은 필수입니다."
            );
        }
        boolean hasProblemProgress = problemProgressConceptPort.existsByUserId(query.userId());

        // 풀이 이력이 없으면 관심 개념 ID를 조회하고 개념 이름으로 변환합니다.
        if (!hasProblemProgress) {
            List<UUID> interestConceptIds = userInterestConceptPort.getInterestConceptIds(query.userId());
            List<String> conceptNames = conceptLookupPort.getConceptNames(interestConceptIds);

            return DailyQuizConceptCandidates.fromInterests(conceptNames);
        }

        // 풀이 이력이 있으면 WRONG 개념을 조회합니다.
        List<String> wrongConcepts = problemProgressConceptPort.getWrongConceptTags(query.userId());

        // 퀴즈 날짜의 시작 시각을 구하고, 그 전에 SOLVED된 개념을 조회합니다.
        Instant quizDateStart = query.attemptDate()
                .atStartOfDay(QUIZ_ZONE_ID)
                .toInstant();

        List<String> solvedConcepts = problemProgressConceptPort.getSolvedConceptTagsBefore(
                query.userId(),
                quizDateStart
        );

        return DailyQuizConceptCandidates.fromProblemProgress(wrongConcepts, solvedConcepts);
    }
}
