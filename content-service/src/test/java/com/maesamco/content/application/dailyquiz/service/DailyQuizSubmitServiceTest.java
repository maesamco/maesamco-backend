package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.command.DailyQuizSubmitCommand;
import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventData;
import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventPort;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttempt;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttemptItem;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptItemRepository;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptRepository;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizQuestionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Daily Quiz 완료 이벤트를 만들기 전에 내부 데이터 정합성을 검증하는 규칙을 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class DailyQuizSubmitServiceTest {

    private static final Instant TEST_NOW =
            Instant.parse("2026-09-16T03:00:00Z");
    private static final ZoneId QUIZ_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final UUID userId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID submittedQuestionId = UUID.randomUUID();

    @Mock
    private DailyQuizAttemptRepository attemptRepository;

    @Mock
    private DailyQuizAttemptItemRepository attemptItemRepository;

    @Mock
    private DailyQuizQuestionRepository questionRepository;

    @Mock
    private DailyQuizCompletedEventPort completedEventPort;

    private DailyQuizSubmitService submitService;
    private DailyQuizAttempt attempt;

    @BeforeEach
    void setUp() {
        submitService = new DailyQuizSubmitService(
                attemptRepository,
                attemptItemRepository,
                questionRepository,
                completedEventPort,
                Clock.fixed(TEST_NOW, QUIZ_ZONE_ID)
        );
    }

    @Test
    @DisplayName("완료 세트의 배정 문항 수가 totalCount와 다르면 이벤트를 저장하지 않는다")
    void submit_rejectsAttemptItemCountMismatch() {
        stubUntilCompleted(3);
        DailyQuizAttemptItem firstItem = mock(DailyQuizAttemptItem.class);
        DailyQuizAttemptItem secondItem = mock(DailyQuizAttemptItem.class);

        when(attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(attemptId))
                .thenReturn(List.of(firstItem, secondItem));

        assertThatThrownBy(() -> submitService.submit(command()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
                )
                .hasMessage("완료된 Daily Quiz의 배정 문항 수가 전체 문항 수와 일치하지 않습니다.");

        verifyNoInteractions(completedEventPort);
    }

    @Test
    @DisplayName("완료 세트의 문제 버전 일부가 없으면 이벤트를 저장하지 않는다")
    void submit_rejectsMissingQuestionVersions() {
        stubUntilCompleted(2);

        UUID firstQuestionId = UUID.randomUUID();
        UUID secondQuestionId = UUID.randomUUID();
        DailyQuizAttemptItem firstItem = attemptItem(firstQuestionId);
        DailyQuizAttemptItem secondItem = attemptItem(secondQuestionId);
        DailyQuizQuestion firstQuestion = question(firstQuestionId);

        when(attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(attemptId))
                .thenReturn(List.of(firstItem, secondItem));
        when(questionRepository.findAllById(List.of(firstQuestionId, secondQuestionId)))
                .thenReturn(List.of(firstQuestion));

        assertThatThrownBy(() -> submitService.submit(command()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
                )
                .hasMessage("완료된 Daily Quiz의 문제 버전 일부를 찾을 수 없습니다.");

        verifyNoInteractions(completedEventPort);
    }

    @Test
    @DisplayName("조회 결과에 배정된 문제 버전이 없으면 이벤트를 저장하지 않는다")
    void submit_rejectsMismatchedQuestionVersionResult() {
        stubUntilCompleted(2);

        UUID firstQuestionId = UUID.randomUUID();
        UUID missingQuestionId = UUID.randomUUID();
        UUID unrelatedQuestionId = UUID.randomUUID();
        DailyQuizAttemptItem firstItem = attemptItem(firstQuestionId);
        DailyQuizAttemptItem missingItem = attemptItem(missingQuestionId);
        DailyQuizQuestion firstQuestion = question(firstQuestionId);
        DailyQuizQuestion unrelatedQuestion = question(unrelatedQuestionId);

        when(firstItem.getCorrect()).thenReturn(true);
        when(firstQuestion.getConceptTags()).thenReturn(List.of("반복문"));

        when(attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(attemptId))
                .thenReturn(List.of(firstItem, missingItem));
        when(questionRepository.findAllById(List.of(firstQuestionId, missingQuestionId)))
                .thenReturn(List.of(firstQuestion, unrelatedQuestion));

        assertThatThrownBy(() -> submitService.submit(command()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
                )
                .hasMessage(
                        "완료된 Daily Quiz의 문제 버전을 찾을 수 없습니다. questionId="
                                + missingQuestionId
                );

        verifyNoInteractions(completedEventPort);
    }

    @Test
    @DisplayName("완료 문항에 채점 결과가 없으면 이벤트를 저장하지 않는다")
    void submit_rejectsMissingCorrectResult() {
        stubUntilCompleted(1);

        UUID questionId = UUID.randomUUID();
        DailyQuizAttemptItem item = attemptItem(questionId);
        DailyQuizQuestion question = question(questionId);

        when(item.getCorrect()).thenReturn(null);
        when(attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(attemptId))
                .thenReturn(List.of(item));
        when(questionRepository.findAllById(List.of(questionId)))
                .thenReturn(List.of(question));

        assertThatThrownBy(() -> submitService.submit(command()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
                )
                .hasMessage(
                        "완료된 Daily Quiz 문항에 채점 결과가 없습니다. questionId="
                                + questionId
                );

        verifyNoInteractions(completedEventPort);
    }

    @Test
    @DisplayName("마지막 문항 제출 시 완료 의미 데이터를 이벤트 포트로 전달한다")
    void submit_publishesCompletedEventData() {
        stubUntilCompleted(1);

        UUID questionId = UUID.randomUUID();
        DailyQuizAttemptItem item = attemptItem(questionId);
        DailyQuizQuestion question = question(questionId);

        when(attempt.getCorrectCount()).thenReturn(1);
        when(attempt.getCompletedAt()).thenReturn(TEST_NOW);
        when(item.getCorrect()).thenReturn(true);
        when(question.getConceptTags()).thenReturn(List.of("메서드"));
        when(attemptItemRepository.findAllByAttemptIdOrderByQuestionOrder(attemptId))
                .thenReturn(List.of(item));
        when(questionRepository.findAllById(List.of(questionId)))
                .thenReturn(List.of(question));

        submitService.submit(command());

        ArgumentCaptor<DailyQuizCompletedEventData> eventDataCaptor =
                ArgumentCaptor.forClass(DailyQuizCompletedEventData.class);
        verify(completedEventPort).publish(eventDataCaptor.capture());

        DailyQuizCompletedEventData eventData = eventDataCaptor.getValue();
        assertThat(eventData.occurredAt()).isEqualTo(TEST_NOW);
        assertThat(eventData.quizAttemptId()).isEqualTo(attemptId);
        assertThat(eventData.userId()).isEqualTo(userId);
        assertThat(eventData.correctCount()).isEqualTo(1);
        assertThat(eventData.totalCount()).isEqualTo(1);
        assertThat(eventData.completedAt()).isEqualTo(TEST_NOW);
        assertThat(eventData.questionResults()).singleElement().satisfies(result -> {
            assertThat(result.questionVersionId()).isEqualTo(questionId);
            assertThat(result.conceptTags()).containsExactly("메서드");
            assertThat(result.correct()).isTrue();
        });
    }

    private void stubUntilCompleted(int totalCount) {
        attempt = mock(DailyQuizAttempt.class);
        DailyQuizAttemptItem submittedItem = mock(DailyQuizAttemptItem.class);
        DailyQuizQuestion submittedQuestion = mock(DailyQuizQuestion.class);

        when(attempt.getId()).thenReturn(attemptId);
        when(attempt.getUserId()).thenReturn(userId);
        when(attempt.getAttemptDate()).thenReturn(LocalDate.of(2026, 9, 16));
        when(attempt.isInProgress()).thenReturn(true);
        when(attempt.getTotalCount()).thenReturn(totalCount);
        when(submittedItem.isAnswered()).thenReturn(false);
        when(submittedQuestion.getId()).thenReturn(submittedQuestionId);
        when(submittedQuestion.isCorrect("정답")).thenReturn(true);

        when(attemptRepository.findByIdForUpdate(attemptId))
                .thenReturn(Optional.of(attempt));
        when(attemptItemRepository.findByAttemptIdAndQuestionId(
                attemptId,
                submittedQuestionId
        )).thenReturn(Optional.of(submittedItem));
        when(questionRepository.findById(submittedQuestionId))
                .thenReturn(Optional.of(submittedQuestion));
        when(attemptItemRepository.submitIfUnanswered(
                attemptId,
                submittedQuestionId,
                "정답",
                true,
                TEST_NOW
        )).thenReturn(1);
        when(attemptItemRepository.countUnansweredByAttemptId(attemptId))
                .thenReturn(0L);
        when(attemptItemRepository.countCorrectByAttemptId(attemptId))
                .thenReturn(1L);
        when(attemptRepository.save(attempt)).thenReturn(attempt);
    }

    private DailyQuizSubmitCommand command() {
        return DailyQuizSubmitCommand.from(
                userId,
                attemptId,
                submittedQuestionId,
                "정답"
        );
    }

    private DailyQuizAttemptItem attemptItem(UUID questionId) {
        DailyQuizAttemptItem item = mock(DailyQuizAttemptItem.class);
        when(item.getQuestionId()).thenReturn(questionId);
        return item;
    }

    private DailyQuizQuestion question(UUID questionId) {
        DailyQuizQuestion question = mock(DailyQuizQuestion.class);
        when(question.getId()).thenReturn(questionId);
        return question;
    }
}
