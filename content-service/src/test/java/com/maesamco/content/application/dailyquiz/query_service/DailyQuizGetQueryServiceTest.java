package com.maesamco.content.application.dailyquiz.query_service;

import com.maesamco.content.application.dailyquiz.query.DailyQuizGetQuery;
import com.maesamco.content.application.dailyquiz.result.DailyQuizGetResult;
import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionGetResult;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttempt;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttemptItem;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttemptStatus;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptItemRepository;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptRepository;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizQuestionRepository;
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

    @Test
    void 배정된_문제_버전이_없으면_INTERNAL_SERVER_ERROR를_반환한다() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 11);
        DailyQuizAttempt attempt = mock(DailyQuizAttempt.class);
        DailyQuizAttemptItem attemptItem = mock(DailyQuizAttemptItem.class);

        when(attempt.getId()).thenReturn(attemptId);
        when(attemptItem.getQuestionId()).thenReturn(questionId);
        when(attemptRepository.findByUserIdAndAttemptDate(userId, attemptDate))
                .thenReturn(Optional.of(attempt));
        when(attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(attemptId))
                .thenReturn(List.of(attemptItem));
        when(questionRepository.findAllById(List.of(questionId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> queryService.get(DailyQuizGetQuery.from(userId)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
                )
                .hasMessage(
                        "배정된 Daily Quiz 문제 버전을 찾을 수 없습니다. questionId="
                                + questionId
                );
    }

    @Test
    void 제출한_문항은_제출_답안과_정답을_함께_반환한다() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 11);
        DailyQuizAttempt attempt = readableAttempt(attemptId, DailyQuizAttemptStatus.IN_PROGRESS, TEST_NOW);
        DailyQuizAttemptItem attemptItem = mock(DailyQuizAttemptItem.class);
        DailyQuizQuestion question = mock(DailyQuizQuestion.class);

        when(attemptRepository.findByUserIdAndAttemptDate(userId, attemptDate))
                .thenReturn(Optional.of(attempt));
        when(attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(attemptId))
                .thenReturn(List.of(attemptItem));
        when(attemptItem.getQuestionId()).thenReturn(questionId);
        when(attemptItem.getQuestionOrder()).thenReturn(1);
        when(attemptItem.isAnswered()).thenReturn(true);
        when(attemptItem.getCorrect()).thenReturn(false);
        when(attemptItem.getUserAnswer()).thenReturn("B");
        when(questionRepository.findAllById(List.of(questionId))).thenReturn(List.of(question));
        when(question.getId()).thenReturn(questionId);
        when(question.getQuestionGroupId()).thenReturn(UUID.randomUUID());
        when(question.getVersionNo()).thenReturn(1);
        when(question.getProblemType()).thenReturn(DailyQuizProblemType.MULTIPLE_CHOICE);
        when(question.getQuestionText()).thenReturn("올바른 답을 선택하세요.");
        when(question.getChoices()).thenReturn(List.of("A", "B", "C", "D"));
        when(question.getAnswer()).thenReturn("A");

        DailyQuizGetResult result = queryService.get(DailyQuizGetQuery.from(userId));

        DailyQuizQuestionGetResult questionResult = result.questions().get(0);
        assertThat(questionResult.answered()).isTrue();
        assertThat(questionResult.correct()).isFalse();
        assertThat(questionResult.submittedResponse()).isEqualTo("B");
        assertThat(questionResult.correctAnswer()).isEqualTo("A");
    }

    @Test
    void 미제출_문항은_제출_답안과_정답을_반환하지_않는다() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 11);
        DailyQuizAttempt attempt = readableAttempt(attemptId, DailyQuizAttemptStatus.IN_PROGRESS, TEST_NOW);
        DailyQuizAttemptItem attemptItem = mock(DailyQuizAttemptItem.class);
        DailyQuizQuestion question = mock(DailyQuizQuestion.class);

        when(attemptRepository.findByUserIdAndAttemptDate(userId, attemptDate))
                .thenReturn(Optional.of(attempt));
        when(attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(attemptId))
                .thenReturn(List.of(attemptItem));
        when(attemptItem.getQuestionId()).thenReturn(questionId);
        when(attemptItem.getQuestionOrder()).thenReturn(1);
        when(attemptItem.isAnswered()).thenReturn(false);
        when(questionRepository.findAllById(List.of(questionId))).thenReturn(List.of(question));
        when(question.getId()).thenReturn(questionId);
        when(question.getQuestionGroupId()).thenReturn(UUID.randomUUID());
        when(question.getVersionNo()).thenReturn(1);
        when(question.getProblemType()).thenReturn(DailyQuizProblemType.MULTIPLE_CHOICE);
        when(question.getQuestionText()).thenReturn("올바른 답을 선택하세요.");
        when(question.getChoices()).thenReturn(List.of("A", "B", "C", "D"));

        DailyQuizGetResult result = queryService.get(DailyQuizGetQuery.from(userId));

        DailyQuizQuestionGetResult questionResult = result.questions().get(0);
        assertThat(questionResult.answered()).isFalse();
        assertThat(questionResult.correct()).isNull();
        assertThat(questionResult.submittedResponse()).isNull();
        assertThat(questionResult.correctAnswer()).isNull();
        // 미제출 문항은 정답 자체를 조회하지 않아야 한다 — 값이 null인 것과, 애초에
        // 노출 경로를 안 타는 것은 다르며 후자가 이 정책의 실제 의도다.
        verify(question, never()).getAnswer();
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
