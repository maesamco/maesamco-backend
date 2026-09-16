package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.command.DailyQuizSubmitCommand;
import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventData;
import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventPort;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSubmitResult;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttempt;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttemptItem;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptItemRepository;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptRepository;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizQuestionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily Quiz 문항 제출의 잠금·검증·채점·완료 처리를 조정합니다.
 */
@Service
@RequiredArgsConstructor
public class DailyQuizSubmitService {

    private final DailyQuizAttemptRepository attemptRepository;
    private final DailyQuizAttemptItemRepository attemptItemRepository;
    private final DailyQuizQuestionRepository questionRepository;
    private final DailyQuizCompletedEventPort completedEventPort;
    private final Clock dailyQuizClock;

    @Transactional
    public DailyQuizSubmitResult submit(DailyQuizSubmitCommand command) {
        // command 자체가 null이면 INVALID_INPUT_VALUE 예외를 발생
        if (command == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "제출 내용은 필수입니다.");
        }

        // 현재 시각을 한 번만 구하고, Daily Quiz Clock 기준 오늘 날짜를 계산
        Instant now = dailyQuizClock.instant();
        LocalDate today = LocalDate.ofInstant(now, dailyQuizClock.getZone());

        // quizAttemptId로 Attempt를 쓰기 잠금 조회
        DailyQuizAttempt attempt = attemptRepository
                .findByIdForUpdate(command.quizAttemptId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.QUIZ_NOT_FOUND
                ));

        // 잠근 Attempt의 userId와 command.userId가 같은지 확인
        if (!attempt.getUserId().equals(command.userId())) {
            // 다른 사용자의 세트도 존재하지 않는 세트와 동일하게 처리해
            // quizAttemptId의 존재 여부가 응답을 통해 노출되지 않도록 합니다.
            throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
        }

        // Attempt의 attemptDate가 오늘보다 이전인지 확인
        if (attempt.getAttemptDate().isBefore(today)) {
            throw new BusinessException(ErrorCode.QUIZ_EXPIRED);
        }

        // attemptId와 questionVersionId로 배정 문항을 조회
        DailyQuizAttemptItem attemptItem = attemptItemRepository
                .findByAttemptIdAndQuestionId(attempt.getId(), command.questionVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_ASSIGNED));

        // 배정 문항이 이미 답변됐는지 먼저 확인
        if (attemptItem.isAnswered()) {
            throw new BusinessException(ErrorCode.ALREADY_SUBMITTED);
        }

        // 아직 미제출인 경우에만 Attempt가 IN_PROGRESS인지 확인
        if (!attempt.isInProgress()) {
            throw new BusinessException(ErrorCode.INVALID_QUIZ_STATUS);
        }

        // questionVersionId로 문제 버전을 조회
        DailyQuizQuestion question = questionRepository
                .findById(command.questionVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "배정된 Daily Quiz 문제 버전을 찾을 수 없습니다. questionId=" + command.questionVersionId()));

        // DailyQuizQuestion.isCorrect(command.response())로 즉시 채점
        boolean correct = question.isCorrect(command.response());

        // submitIfUnanswered(...)로 답안·정답 여부·제출 시각을 조건부 저장
        // 갱신 행 수가 1이 아니면 동시 제출로 간주하여 ALREADY_SUBMITTED로 처리
        int updatedCount = attemptItemRepository.submitIfUnanswered(
                attempt.getId(),
                question.getId(),
                command.response(),
                correct,
                now
        );

        if (updatedCount != 1) {
            throw new BusinessException(ErrorCode.ALREADY_SUBMITTED);
        }

        // 해당 세트의 미제출 문항 수를 조회하세요.
        // 0보다 크면 attemptCompleted=false인 중간 제출 Result를 반환
        long unansweredCount = attemptItemRepository.countUnansweredByAttemptId(attempt.getId());
        if (unansweredCount > 0) {
            return new DailyQuizSubmitResult(
                    question.getId(),
                    correct,
                    false,
                    null,
                    null
            );
        }

        // 미제출 문항이 0이면 정답 수를 조회하고 int로 안전하게 변환
        int correctCount = Math.toIntExact(
                attemptItemRepository.countCorrectByAttemptId(attempt.getId())
        );

        // attempt.complete(correctCount, now)를 호출해 세트를 완료
        attempt.complete(correctCount, now);
        DailyQuizAttempt completedAttempt = attemptRepository.save(attempt);

        // 완료된 전체 문항의 문제 버전·개념·정답 여부를 이벤트 의미 데이터로 조립
        List<DailyQuizCompletedEventData.QuestionResult> questionResults =
                createQuestionResults(completedAttempt);

        DailyQuizCompletedEventData eventData =
                new DailyQuizCompletedEventData(
                        now,
                        completedAttempt.getId(),
                        completedAttempt.getUserId(),
                        completedAttempt.getCorrectCount(),
                        completedAttempt.getTotalCount(),
                        completedAttempt.getCompletedAt(),
                        questionResults
                );

        // 같은 트랜잭션 안에서 출력 포트를 호출하며, 직렬화와 Outbox 저장은 Adapter가 담당
        completedEventPort.publish(eventData);

        // attemptCompleted=true와 correctCount, totalCount를 담은 Result를 반환
        return new DailyQuizSubmitResult(
                question.getId(),
                correct,
                true,
                correctCount,
                completedAttempt.getTotalCount()
        );
    }

    /**
     * 완료된 세트의 문항 결과를 노출 순서대로 이벤트 항목으로 변환
     */
    private List<DailyQuizCompletedEventData.QuestionResult> createQuestionResults(
            DailyQuizAttempt attempt
    ) {
        List<DailyQuizAttemptItem> attemptItems =
                attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(attempt.getId());

        if (attemptItems.size() != attempt.getTotalCount()) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "완료된 Daily Quiz의 배정 문항 수가 전체 문항 수와 일치하지 않습니다."
            );
        }

        List<UUID> questionIds =
                attemptItems.stream()
                        .map(DailyQuizAttemptItem::getQuestionId)
                        .toList();

        Map<UUID, DailyQuizQuestion> questionsById = new HashMap<>();
        questionRepository.findAllById(questionIds)
                .forEach(question -> questionsById.put(question.getId(), question));

        if (questionsById.size() != questionIds.size()) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "완료된 Daily Quiz의 문제 버전 일부를 찾을 수 없습니다."
            );
        }

        return attemptItems.stream()
                .map(item -> toQuestionResult(item, questionsById))
                .toList();
    }

    private DailyQuizCompletedEventData.QuestionResult toQuestionResult(
            DailyQuizAttemptItem attemptItem,
            Map<UUID, DailyQuizQuestion> questionsById
    ) {
        DailyQuizQuestion question = questionsById.get(attemptItem.getQuestionId());
        if (question == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "완료된 Daily Quiz의 문제 버전을 찾을 수 없습니다. questionId="
                            + attemptItem.getQuestionId()
            );
        }

        Boolean correct = attemptItem.getCorrect();
        if (correct == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "완료된 Daily Quiz 문항에 채점 결과가 없습니다. questionId="
                            + attemptItem.getQuestionId()
            );
        }

        return new DailyQuizCompletedEventData.QuestionResult(
                question.getId(),
                question.getConceptTags(),
                correct
        );
    }
}
