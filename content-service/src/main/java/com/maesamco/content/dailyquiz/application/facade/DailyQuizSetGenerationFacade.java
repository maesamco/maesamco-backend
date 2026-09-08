package com.maesamco.content.dailyquiz.application.facade;

import com.maesamco.content.dailyquiz.application.command.DailyQuizSetCreateCommand;
import com.maesamco.content.dailyquiz.application.command.DailyQuizSetGenerationCommand;
import com.maesamco.content.dailyquiz.application.persistence_service.DailyQuizSetPersistenceService;
import com.maesamco.content.dailyquiz.application.result.DailyQuizQuestionSourcingResult;
import com.maesamco.content.dailyquiz.application.result.DailyQuizSetGenerationResult;
import com.maesamco.content.dailyquiz.application.service.DailyQuizConceptSlotSelector;
import com.maesamco.content.dailyquiz.domain.ConceptSlots;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.maesamco.content.global.util.DataIntegrityViolations.isUniqueViolation;

/**
 * Daily Quiz 세트 생성에 필요한 조회, 문항 확보, 저장 흐름을 조율합니다.
 */
@Component
@RequiredArgsConstructor
public class DailyQuizSetGenerationFacade {

    private final DailyQuizAttemptRepository attemptRepository;
    private final DailyQuizConceptSlotSelector conceptSlotSelector;
    private final DailyQuizQuestionSourcingFacade questionSourcingFacade;
    private final DailyQuizSetPersistenceService persistenceService;

    public DailyQuizSetGenerationResult generate(
            DailyQuizSetGenerationCommand command
    ) {
        if (command == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "Daily Quiz 세트 생성 요청은 필수입니다."
            );
        }

        // 오늘 세트가 이미 존재하면 ALREADY_EXISTS를 반환합니다.
        if (attemptRepository.existsByUserIdAndAttemptDate(command.userId(), command.attemptDate())) {
            return DailyQuizSetGenerationResult.alreadyExists();
        }

        // 개념 슬롯을 만들 수 없으면 NO_AVAILABLE_CONCEPTS를 반환합니다.
        Optional<ConceptSlots> selectedConceptSlots = conceptSlotSelector.select(command.conceptCandidates());

        if (selectedConceptSlots.isEmpty()) {
            return DailyQuizSetGenerationResult.noAvailableConcepts();
        }

        ConceptSlots conceptSlots = selectedConceptSlots.get();

        // 개념 슬롯을 기준으로 기존 문항을 재사용하고 부족한 문항은 AI로 확보합니다.
        DailyQuizQuestionSourcingResult sourcingResult = questionSourcingFacade.sourceQuestions(conceptSlots);

        // 확보한 문항이 3개 미만이면 INSUFFICIENT_QUESTIONS를 반환합니다.
        if (!sourcingResult.canCreateQuiz()) {
            return DailyQuizSetGenerationResult.insufficientQuestions(sourcingResult.questions().size());
        }

        // 최종 문항 ID로 DailyQuizSetCreateCommand를 생성합니다.
        List<UUID> questionIds = sourcingResult.questions().stream()
                .map(DailyQuizQuestion::getId)
                .toList();

        DailyQuizSetCreateCommand createCommand = DailyQuizSetCreateCommand.from(
                command.userId(),
                command.attemptDate(),
                questionIds
        );
        // PersistenceService를 호출해 세트와 배정 문항을 저장합니다.
        try {
            return persistenceService.create(createCommand);
        } catch (DataIntegrityViolationException exception) {
            boolean sameDateAttemptExists =
                    attemptRepository.existsByUserIdAndAttemptDate(
                            command.userId(),
                            command.attemptDate()
                    );

            if (isUniqueViolation(exception) && sameDateAttemptExists) {
                return DailyQuizSetGenerationResult.alreadyExists();
            }

            throw exception;
        }
    }
}
