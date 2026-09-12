package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.application.command.DailyQuizSubmitCommand;
import com.maesamco.content.dailyquiz.application.result.DailyQuizSubmitResult;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptItem;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptItemRepository;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptRepository;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Daily Quiz 문항 제출의 잠금·검증·채점·완료 처리를 조정합니다.
 */
@Service
@RequiredArgsConstructor
public class DailyQuizSubmitService {

    private final DailyQuizAttemptRepository attemptRepository;
    private final DailyQuizAttemptItemRepository attemptItemRepository;
    private final DailyQuizQuestionRepository questionRepository;
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
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
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
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "배정된 Daily Quiz 문제 버전을 찾을 수 없습니다. questionId="
                                + command.questionVersionId()
                ));

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
        attemptRepository.save(attempt);

        // attemptCompleted=true와 correctCount, totalCount를 담은 Result를 반환
        return new DailyQuizSubmitResult(
                question.getId(),
                correct,
                true,
                correctCount,
                attempt.getTotalCount()
        );
    }
}
