package com.maesamco.content.dailyquiz.application.query_service;

import com.maesamco.content.dailyquiz.application.query.DailyQuizGetQuery;
import com.maesamco.content.dailyquiz.application.result.DailyQuizGetResult;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptStatus;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptItemRepository;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptRepository;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 오늘의 Daily Quiz 조회와 최초 시작 처리 규칙을 검증하는 단위 테스트입니다.
 *
 * 고정 Clock을 사용하여 조회 날짜와 startedAt을 결정적으로 검증
 */
@ExtendWith(MockitoExtension.class)
class DailyQuizGetQueryServiceTest {

    private static final Instant TEST_NOW = Instant.parse("2026-09-11T03:00:00Z");
    private static final ZoneId QUIZ_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Mock
    private DailyQuizAttemptRepository attemptRepository;

    @Mock
    private DailyQuizAttemptItemRepository attemptItemRepository;

    @Mock
    private DailyQuizQuestionRepository questionRepository;

    private DailyQuizGetQueryService queryService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(TEST_NOW, QUIZ_ZONE_ID);
        queryService = new DailyQuizGetQueryService(
                attemptRepository,
                attemptItemRepository,
                questionRepository,
                fixedClock
        );
    }

    @Test
    void IN_PROGRESS_세트를_반복_조회하면_기존_startedAt을_유지한다() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 11);
        Instant originalStartedAt = TEST_NOW.minusSeconds(60);
        DailyQuizAttempt attempt = readableAttempt(
                attemptId,
                DailyQuizAttemptStatus.IN_PROGRESS,
                originalStartedAt
        );
        when(attemptRepository.findByUserIdAndAttemptDate(userId, attemptDate))
                .thenReturn(Optional.of(attempt));
        stubEmptyQuestions(attemptId);

        // 서비스 실행
        DailyQuizGetResult result = queryService.get(DailyQuizGetQuery.from(userId));

        assertThat(result.attemptStatus()).isEqualTo(DailyQuizAttemptStatus.IN_PROGRESS);
        assertThat(result.startedAt()).isEqualTo(originalStartedAt);
        verify(attemptRepository, never()).startIfReady(any(), any());
    }

    @Test
    void COMPLETED_세트를_조회하면_시작_UPDATE를_호출하지_않는다() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 11);
        Instant originalStartedAt = TEST_NOW.minusSeconds(180);
        DailyQuizAttempt attempt = readableAttempt(
                attemptId,
                DailyQuizAttemptStatus.COMPLETED,
                originalStartedAt
        );
        when(attemptRepository.findByUserIdAndAttemptDate(userId, attemptDate))
                .thenReturn(Optional.of(attempt));
        stubEmptyQuestions(attemptId);

        DailyQuizGetResult result = queryService.get(DailyQuizGetQuery.from(userId));

        assertThat(result.attemptStatus()).isEqualTo(DailyQuizAttemptStatus.COMPLETED);
        assertThat(result.startedAt()).isEqualTo(originalStartedAt);
        // 상태 변경하는 지 확인
        verify(attemptRepository, never()).startIfReady(any(), any());
    }

    @Test
    void 오늘의_세트가_없으면_QUIZ_NOT_FOUND를_반환한다() {
        UUID userId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 11);
        when(attemptRepository.findByUserIdAndAttemptDate(userId, attemptDate))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.get(DailyQuizGetQuery.from(userId)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.QUIZ_NOT_FOUND)
                );
        verifyNoInteractions(attemptItemRepository, questionRepository);
    }

    private DailyQuizAttempt readableAttempt(
            UUID attemptId,
            DailyQuizAttemptStatus status,
            Instant startedAt
    ) {
        DailyQuizAttempt attempt = mock(DailyQuizAttempt.class);
        when(attempt.isReady()).thenReturn(false);
        when(attempt.getId()).thenReturn(attemptId);
        when(attempt.getStatus()).thenReturn(status);
        when(attempt.getTotalCount()).thenReturn(3);
        when(attempt.getStartedAt()).thenReturn(startedAt);
        return attempt;
    }

    private void stubEmptyQuestions(UUID attemptId) {
        when(attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(attemptId))
                .thenReturn(List.of());
        when(questionRepository.findAllById(List.<UUID>of()))
                .thenReturn(List.of());
    }
}
