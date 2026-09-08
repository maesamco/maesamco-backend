package com.maesamco.content.dailyquiz.application.query_service;

import com.maesamco.content.dailyquiz.application.query.DailyQuizGetQuery;
import com.maesamco.content.dailyquiz.application.result.DailyQuizGetResult;
import com.maesamco.content.dailyquiz.application.result.DailyQuizQuestionGetResult;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 인증된 사용자의 오늘 Daily Quiz 세트를 조회하고 최초 시작을 처리합니다.
 *
 * READY 상태의 세트는 조건부 UPDATE로 IN_PROGRESS 상태로 전환한 뒤
 * 최신 상태를 다시 조회합니다. 배정 문항과 문제 버전은 각각 한 번씩 조회하고,
 * 문항 순서를 유지하여 최종 조회 결과를 구성합니다.
 */
@Service
@RequiredArgsConstructor
public class DailyQuizGetQueryService {

    private static final ZoneId QUIZ_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final DailyQuizAttemptRepository attemptRepository;
    private final DailyQuizAttemptItemRepository attemptItemRepository;
    private final DailyQuizQuestionRepository questionRepository;

    @Transactional
    public DailyQuizGetResult get(DailyQuizGetQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Daily Quiz 조회 조건은 필수입니다.");
        }

        Instant now = Instant.now();
        LocalDate attemptDate = LocalDate.ofInstant(now, QUIZ_ZONE_ID);

        DailyQuizAttempt attempt = findTodayAttempt(
                query.userId(),
                attemptDate
        );

        // READY 상태이면 조건부 UPDATE로 IN_PROGRESS와 startedAt을 함께 기록
        if (attempt.isReady()) {
            // 동시 요청에서 다른 요청이 먼저 처리하면 변경 행 수는 0입니다.
            // 이는 정상적인 재조회 상황이므로 예외로 처리하지 않습니다.
            attemptRepository.startIfReady(attempt.getId(), now);

            // 벌크 UPDATE가 영속성 컨텍스트를 비우므로 최신 상태를 다시 조회
            attempt = findTodayAttempt(
                    query.userId(),
                    attemptDate
            );
        }

        // 세트에 배정된 문항을 questionOrder 오름차순으로 조회
        List<DailyQuizAttemptItem> attemptItems =
                attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(
                        attempt.getId()
                );

        // 배정된 문제 버전을 일괄 조회하기 위해 문제 ID 목록을 추출
        List<UUID> questionIds = attemptItems.stream()
                .map(DailyQuizAttemptItem::getQuestionId)
                .toList();

        // 배정된 문제 버전들을 한 번에 조회하고 ID 기준 Map으로 변환
        Map<UUID, DailyQuizQuestion> questionById =
                questionRepository.findAllById(questionIds).stream()
                        .collect(Collectors.toMap(
                                DailyQuizQuestion::getId,
                                Function.identity()
                        ));

        // Attempt Item 순서를 기준으로 개별 문항 조회 결과를 조립
        List<DailyQuizQuestionGetResult> questions = attemptItems.stream()
                .map(item -> toQuestionResult(
                        item,
                        findAssignedQuestion(questionById, item.getQuestionId())
                ))
                .toList();

        return new DailyQuizGetResult(
                attempt.getId(),
                attempt.getStatus(),
                attempt.getTotalCount(),
                attempt.getStartedAt(),
                questions
        );
    }


    /**
     * 사용자 ID와 퀴즈 날짜가 모두 일치하는 Daily Quiz 세트를 조회
     * 다른 사용자의 세트가 노출되지 않도록 소유자 조건을 함께 사용하며
     * 오늘 생성된 세트가 없으면 조회 API의 비즈니스 예외 발생
     */
    private DailyQuizAttempt findTodayAttempt(UUID userId, LocalDate attemptDate) {
        return attemptRepository.findByUserIdAndAttemptDate(userId, attemptDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
    }

    /**
     * 일괄 조회한 문제 버전 Map에서 Attempt Item에 배정된 문제를 찾는다.
     */
    private DailyQuizQuestion findAssignedQuestion(Map<UUID, DailyQuizQuestion> questionById, UUID questionId) {
        DailyQuizQuestion question = questionById.get(questionId);

        if (question == null) {
            throw new IllegalStateException("배정된 Daily Quiz 문제 버전을 찾을 수 없습니다. questionId=" + questionId);
        }

        return question;
    }

    /**
     * Attempt Item의 배정 순서 및 제출 상태와 문제 버전의 공개 정보를 결합하여
     * 개별 문항 조회 결과 DTO로 변환합니다.
     */
    private DailyQuizQuestionGetResult toQuestionResult(
            DailyQuizAttemptItem item,
            DailyQuizQuestion question
    ) {
        return new DailyQuizQuestionGetResult(
                question.getQuestionGroupId(),
                question.getId(),
                question.getVersionNo(),
                item.getQuestionOrder(),
                question.getProblemType(),
                question.getQuestionText(),
                question.getChoices(),
                item.isAnswered(),
                item.isAnswered() ? item.getCorrect() : null
        );
    }
}
