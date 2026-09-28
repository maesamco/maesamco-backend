package com.maesamco.content.application.dailyquiz.query_service;

import com.maesamco.content.application.dailyquiz.query.DailyQuizGetQuery;
import com.maesamco.content.application.dailyquiz.result.DailyQuizProgressResult;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttempt;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptItemRepository;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 인증된 사용자의 오늘 Daily Quiz 진행도(상태·완료 문항 수)만 조회합니다.
 *
 * {@link DailyQuizGetQueryService}와 달리 세트를 시작 처리(READY→IN_PROGRESS)하지
 * 않는 순수 조회입니다. 홈 화면처럼 사용자가 실제로 퀴즈를 시작할 의도 없이
 * 진행도만 확인하는 곳에서, 조회만으로 타이머가 시작돼 버리는 부작용 없이 쓰도록
 * 분리했습니다.
 */
@Service
@RequiredArgsConstructor
public class DailyQuizProgressQueryService {

    private final DailyQuizAttemptRepository attemptRepository;
    private final DailyQuizAttemptItemRepository attemptItemRepository;
    private final Clock dailyQuizClock;

    @Transactional(readOnly = true)
    public DailyQuizProgressResult get(DailyQuizGetQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Daily Quiz 조회 조건은 필수입니다.");
        }

        LocalDate attemptDate = LocalDate.now(dailyQuizClock);

        DailyQuizAttempt attempt = attemptRepository
                .findByUserIdAndAttemptDate(query.userId(), attemptDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));

        long unansweredCount = attemptItemRepository.countUnansweredByAttemptId(attempt.getId());
        int completedCount = attempt.getTotalCount() - Math.toIntExact(unansweredCount);

        return new DailyQuizProgressResult(
                attempt.getStatus(),
                completedCount,
                attempt.getTotalCount()
        );
    }
}
