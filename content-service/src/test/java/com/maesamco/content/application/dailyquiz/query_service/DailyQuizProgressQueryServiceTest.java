package com.maesamco.content.application.dailyquiz.query_service;

import com.maesamco.content.application.dailyquiz.query.DailyQuizGetQuery;
import com.maesamco.content.application.dailyquiz.result.DailyQuizProgressResult;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttempt;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttemptStatus;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptItemRepository;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptRepository;
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
 * 오늘의 Daily Quiz 진행도 조회가 세트를 시작 처리하지 않는지, 완료 문항 수를
 * 정확히 계산하는지 검증하는 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class DailyQuizProgressQueryServiceTest {

    private static final Instant TEST_NOW = Instant.parse("2026-09-11T03:00:00Z");
    private static final ZoneId QUIZ_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final LocalDate ATTEMPT_DATE = LocalDate.of(2026, 9, 11);

    @Mock
    private DailyQuizAttemptRepository attemptRepository;

    @Mock
    private DailyQuizAttemptItemRepository attemptItemRepository;

    private DailyQuizProgressQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new DailyQuizProgressQueryService(
                attemptRepository,
                attemptItemRepository,
                Clock.fixed(TEST_NOW, QUIZ_ZONE_ID)
        );
    }

    @Test
    void 진행_중인_세트는_완료_문항_수를_반환하고_시작_처리를_하지_않는다() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        DailyQuizAttempt attempt = mock(DailyQuizAttempt.class);
        when(attempt.getId()).thenReturn(attemptId);
        when(attempt.getStatus()).thenReturn(DailyQuizAttemptStatus.IN_PROGRESS);
        when(attempt.getTotalCount()).thenReturn(5);
        when(attemptRepository.findByUserIdAndAttemptDate(userId, ATTEMPT_DATE))
                .thenReturn(Optional.of(attempt));
        when(attemptItemRepository.countUnansweredByAttemptId(attemptId)).thenReturn(3L);

        DailyQuizProgressResult result = queryService.get(DailyQuizGetQuery.from(userId));

        assertThat(result.attemptStatus()).isEqualTo(DailyQuizAttemptStatus.IN_PROGRESS);
        assertThat(result.completedCount()).isEqualTo(2);
        assertThat(result.totalCount()).isEqualTo(5);
        // 조회만으로 세트가 시작 처리되면 안 된다 — 이 조회의 존재 이유 자체를 검증한다.
        verify(attemptRepository, never()).startIfReady(any(), any());
    }

    @Test
    void 완료된_세트는_전체_문항_수만큼_완료로_반환한다() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        DailyQuizAttempt attempt = mock(DailyQuizAttempt.class);
        when(attempt.getId()).thenReturn(attemptId);
        when(attempt.getStatus()).thenReturn(DailyQuizAttemptStatus.COMPLETED);
        when(attempt.getTotalCount()).thenReturn(5);
        when(attemptRepository.findByUserIdAndAttemptDate(userId, ATTEMPT_DATE))
                .thenReturn(Optional.of(attempt));
        when(attemptItemRepository.countUnansweredByAttemptId(attemptId)).thenReturn(0L);

        DailyQuizProgressResult result = queryService.get(DailyQuizGetQuery.from(userId));

        assertThat(result.attemptStatus()).isEqualTo(DailyQuizAttemptStatus.COMPLETED);
        assertThat(result.completedCount()).isEqualTo(5);
    }

    @Test
    void 오늘의_세트가_없으면_QUIZ_NOT_FOUND를_반환한다() {
        UUID userId = UUID.randomUUID();
        when(attemptRepository.findByUserIdAndAttemptDate(userId, ATTEMPT_DATE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.get(DailyQuizGetQuery.from(userId)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.QUIZ_NOT_FOUND)
                );
        verifyNoInteractions(attemptItemRepository);
    }
}
