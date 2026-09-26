package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.command.DailyQuizSetGenerationCommand;
import com.maesamco.content.application.dailyquiz.exception.DailyQuizUserLookupException;
import com.maesamco.content.application.dailyquiz.exception.DailyQuizUserProcessingException;
import com.maesamco.content.application.dailyquiz.facade.DailyQuizSetGenerationFacade;
import com.maesamco.content.application.dailyquiz.query.DailyQuizConceptCandidatesGetQuery;
import com.maesamco.content.application.dailyquiz.query_service.DailyQuizConceptCandidateQueryService;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSetGenerationResult;
import com.maesamco.content.domain.dailyquiz.DailyQuizConceptCandidates;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 사용자 한 명의 개념 후보를 조회하고 Daily Quiz 세트 생성을 실행하는 서비스
 */
@Service
@RequiredArgsConstructor
public class DailyQuizUserGenerationService {

    private final DailyQuizConceptCandidateQueryService conceptCandidateQueryService;
    private final DailyQuizSetGenerationFacade setGenerationFacade;
    private final DailyQuizAttemptRepository attemptRepository;

    public DailyQuizSetGenerationResult generate(UUID userId, LocalDate attemptDate) {
        try {
            DailyQuizConceptCandidatesGetQuery query =
                    DailyQuizConceptCandidatesGetQuery.from(userId, attemptDate);

            // 재실행 시 이미 생성된 세트는 외부 개념 조회와 문항 확보 전에 건너뜁니다.
            if (attemptRepository.existsByUserIdAndAttemptDate(userId, attemptDate)) {
                return DailyQuizSetGenerationResult.alreadyExists();
            }

            DailyQuizConceptCandidates conceptCandidates;
            try {
                conceptCandidates = conceptCandidateQueryService.get(query);
            } catch (BusinessException exception) {
                if (exception.getErrorCode() != ErrorCode.INVALID_INPUT_VALUE) {
                    throw exception;
                }
                throw new DailyQuizUserProcessingException(userId, attemptDate, exception);
            }

            DailyQuizSetGenerationCommand command = DailyQuizSetGenerationCommand.from(
                    userId,
                    attemptDate,
                    conceptCandidates
            );

            return setGenerationFacade.generate(command);
        } catch (DataIntegrityViolationException exception) {
            throw new DailyQuizUserProcessingException(userId, attemptDate, exception);
        } catch (DailyQuizUserLookupException exception) {
            throw new DailyQuizUserProcessingException(userId, attemptDate, exception);
        }
    }
}
