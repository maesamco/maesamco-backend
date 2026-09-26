package com.maesamco.content.application.dailyquiz.facade;

import com.maesamco.content.application.dailyquiz.command.DailyQuizSetCreateCommand;
import com.maesamco.content.application.dailyquiz.command.DailyQuizSetGenerationCommand;
import com.maesamco.content.application.dailyquiz.persistence_service.DailyQuizSetPersistenceService;
import com.maesamco.content.application.dailyquiz.query_service.DailyQuizConceptCandidateQueryService;
import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionSourcingResult;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSetGenerationResult;
import com.maesamco.content.application.dailyquiz.service.DailyQuizConceptSlotSelector;
import com.maesamco.content.application.dailyquiz.service.DailyQuizFallbackQuestionSelector;
import com.maesamco.content.application.dailyquiz.service.DailyQuizQuestionSlotAllocator;
import com.maesamco.content.domain.dailyquiz.ConceptSlots;
import com.maesamco.content.domain.dailyquiz.DailyQuizQuestionTypePolicy;
import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.maesamco.content.domain.dailyquiz.DailyQuizPolicy.MINIMUM_QUESTION_COUNT;
import static com.maesamco.content.domain.dailyquiz.DailyQuizPolicy.TARGET_QUESTION_COUNT;
import static com.maesamco.content.global.util.DataIntegrityViolations.isUniqueViolation;

/**
 * Daily Quiz 세트 생성에 필요한 조회, 문항 확보, 저장 흐름을 조율
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyQuizSetGenerationFacade {

    private final DailyQuizAttemptRepository attemptRepository;
    private final DailyQuizConceptSlotSelector conceptSlotSelector;
    private final DailyQuizConceptCandidateQueryService conceptCandidateQueryService;
    private final DailyQuizQuestionSlotAllocator questionSlotAllocator;
    private final DailyQuizQuestionSourcingFacade questionSourcingFacade;
    private final DailyQuizFallbackQuestionSelector fallbackQuestionSelector;
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

        Optional<ConceptSlots> selectedConceptSlots = conceptSlotSelector.select(command.conceptCandidates());
        List<DailyQuizQuestion> personalizedQuestions = List.of();
        if (selectedConceptSlots.isPresent()) {
            QuestionSlots questionSlots = questionSlotAllocator.allocate(selectedConceptSlots.get());
            DailyQuizQuestionSourcingResult sourcingResult = questionSourcingFacade.sourceQuestions(questionSlots);
            personalizedQuestions = supplementWithInterests(
                    command, selectedConceptSlots.get(), sourcingResult.questions()
            );
        }

        List<DailyQuizQuestion> questions = fallbackQuestionSelector.fill(
                command.userId(), command.attemptDate(), personalizedQuestions
        );
        if (questions.size() < TARGET_QUESTION_COUNT) {
            log.warn("Daily Quiz 목표 문항 부족. userId={}, attemptDate={}, questionCount={}",
                    command.userId(), command.attemptDate(), questions.size());
        }
        if (questions.size() < MINIMUM_QUESTION_COUNT) {
            return DailyQuizSetGenerationResult.insufficientQuestions(questions.size());
        }

        // 최종 문항 ID로 DailyQuizSetCreateCommand를 생성합니다.
        List<UUID> questionIds = questions.stream()
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

    private List<DailyQuizQuestion> supplementWithInterests(
            DailyQuizSetGenerationCommand command,
            ConceptSlots initialSlots,
            List<DailyQuizQuestion> selectedQuestions
    ) {
        if (selectedQuestions.size() >= TARGET_QUESTION_COUNT) {
            return selectedQuestions;
        }

        List<String> interests = command.conceptCandidates().interestConcepts();
        long progressConceptCount = Stream.concat(
                command.conceptCandidates().wrongConcepts().stream(),
                command.conceptCandidates().correctConcepts().stream()
        ).distinct().count();
        if (interests.isEmpty() && command.conceptCandidates().hasProblemProgress()
                && progressConceptCount >= TARGET_QUESTION_COUNT) {
            interests = conceptCandidateQueryService.getInterestConceptTags(command.userId());
        }

        Set<String> initialConcepts = new HashSet<>(initialSlots.values());
        List<String> alternateInterests = interests.stream()
                .distinct()
                .filter(interest -> !initialConcepts.contains(interest))
                .toList();
        if (alternateInterests.isEmpty()) {
            return selectedQuestions;
        }

        List<DailyQuizProblemType> missingTypes = new ArrayList<>(DailyQuizQuestionTypePolicy.targetTypes());
        selectedQuestions.forEach(question -> missingTypes.remove(question.getProblemType()));
        int missingCount = TARGET_QUESTION_COUNT - selectedQuestions.size();
        List<QuestionSlot> alternateSlots = IntStream.range(0, missingCount)
                .mapToObj(index -> new QuestionSlot(
                        alternateInterests.get(index % alternateInterests.size()),
                        missingTypes.get(index)
                ))
                .toList();

        List<DailyQuizQuestion> interestQuestions = questionSourcingFacade
                .sourceQuestions(new QuestionSlots(alternateSlots))
                .questions();
        Map<UUID, DailyQuizQuestion> distinctQuestions = new LinkedHashMap<>();
        Stream.concat(selectedQuestions.stream(), interestQuestions.stream())
                .forEach(question -> distinctQuestions.putIfAbsent(question.getId(), question));
        return List.copyOf(distinctQuestions.values());
    }
}
