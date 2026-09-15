package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.command.DailyQuizSetGenerationCommand;
import com.maesamco.content.application.dailyquiz.exception.DailyQuizUserProcessingException;
import com.maesamco.content.application.dailyquiz.facade.DailyQuizSetGenerationFacade;
import com.maesamco.content.application.dailyquiz.query.DailyQuizConceptCandidatesGetQuery;
import com.maesamco.content.application.dailyquiz.query_service.DailyQuizConceptCandidateQueryService;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSetGenerationResult;
import com.maesamco.content.domain.dailyquiz.DailyQuizConceptCandidates;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
// import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 사용자 한 명의 개념 후보를 조회하고 Daily Quiz 세트 생성을 실행하는 서비스
 *
 * TODO: ProblemProgressConceptPort와 ConceptLookupPort의 실제 구현이 병합되면
 * DailyQuizConceptCandidateQueryService와 이 클래스를 Spring Bean으로 등록합니다.
 */
// @Service
@RequiredArgsConstructor
public class DailyQuizUserGenerationService {

    private final DailyQuizConceptCandidateQueryService conceptCandidateQueryService;
    private final DailyQuizSetGenerationFacade setGenerationFacade;

    public DailyQuizSetGenerationResult generate(UUID userId, LocalDate attemptDate) {
        // userId와 attemptDate로 DailyQuizConceptCandidatesGetQuery를 생성합니다.
        DailyQuizConceptCandidatesGetQuery query =
                DailyQuizConceptCandidatesGetQuery.from(userId, attemptDate);

        // 특정 사용자의 개념 후보 데이터가 도메인 계약을 위반하면 사용자 단위 예외로 변환합니다.
        DailyQuizConceptCandidates conceptCandidates;
        try {
            conceptCandidates = conceptCandidateQueryService.get(query);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.INVALID_INPUT_VALUE) {
                throw exception;
            }
            throw new DailyQuizUserProcessingException(userId, attemptDate, exception);
        }

        // 사용자 ID, 날짜, 개념 후보로 DailyQuizSetGenerationCommand를 생성합니다.
        DailyQuizSetGenerationCommand command = DailyQuizSetGenerationCommand.from(
                userId,
                attemptDate,
                conceptCandidates
        );

        // setGenerationFacade를 호출하고 생성 결과를 반환합니다.
        return setGenerationFacade.generate(command);
    }
}
