package com.maesamco.user.infrastructure.messaging.consumer;

import com.maesamco.user.application.service.ApplyDailyQuizCompletedRewardCommand;
import com.maesamco.user.application.service.ApplyDailyQuizCompletedRewardService;
import com.maesamco.user.infrastructure.messaging.event.DailyQuizCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyQuizCompletedConsumerTest {

    @Mock
    private ApplyDailyQuizCompletedRewardService rewardService;

    @InjectMocks
    private DailyQuizCompletedConsumer consumer;

    @Test
    @DisplayName("DailyQuizCompleted 이벤트를 보상 처리 명령으로 변환한다")
    void consume_delegatesRewardCommand() {
        DailyQuizCompletedEvent event = event();
        when(rewardService.apply(
                org.mockito.ArgumentMatchers.any(
                        ApplyDailyQuizCompletedRewardCommand.class
                )
        )).thenReturn(true);

        consumer.consume(event);

        ArgumentCaptor<ApplyDailyQuizCompletedRewardCommand> captor =
                ArgumentCaptor.forClass(
                        ApplyDailyQuizCompletedRewardCommand.class
                );
        verify(rewardService).apply(captor.capture());

        ApplyDailyQuizCompletedRewardCommand command = captor.getValue();
        assertThat(command.quizAttemptId()).isEqualTo(event.quizAttemptId());
        assertThat(command.userId()).isEqualTo(event.userId());
        assertThat(command.completedAt()).isEqualTo(event.completedAt());
    }

    @Test
    @DisplayName("중복 이벤트도 정상 처리로 종료한다")
    void consume_acceptsDuplicatedEvent() {
        DailyQuizCompletedEvent event = event();
        when(rewardService.apply(
                org.mockito.ArgumentMatchers.any(
                        ApplyDailyQuizCompletedRewardCommand.class
                )
        )).thenReturn(false);

        consumer.consume(event);

        verify(rewardService).apply(
                org.mockito.ArgumentMatchers.any(
                        ApplyDailyQuizCompletedRewardCommand.class
                )
        );
    }

    private DailyQuizCompletedEvent event() {
        return new DailyQuizCompletedEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                3,
                5,
                Instant.parse("2026-09-20T15:30:00Z")
        );
    }
}
