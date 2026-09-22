package com.maesamco.user.infrastructure.messaging.consumer;

import com.maesamco.user.application.service.ApplyCoachingCompletedRewardCommand;
import com.maesamco.user.application.service.ApplyCoachingCompletedRewardService;
import com.maesamco.user.infrastructure.messaging.event.CoachingCompletedEvent;
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
class CoachingCompletedConsumerTest {

    @Mock
    private ApplyCoachingCompletedRewardService rewardService;

    @InjectMocks
    private CoachingCompletedConsumer consumer;

    @Test
    @DisplayName("CoachingCompleted 이벤트를 보상 처리 명령으로 변환한다")
    void consume_delegatesRewardCommand() {
        CoachingCompletedEvent event = event();
        when(rewardService.apply(
                org.mockito.ArgumentMatchers.any(
                        ApplyCoachingCompletedRewardCommand.class
                )
        )).thenReturn(true);

        consumer.consume(event);

        ArgumentCaptor<ApplyCoachingCompletedRewardCommand> captor =
                ArgumentCaptor.forClass(
                        ApplyCoachingCompletedRewardCommand.class
                );
        verify(rewardService).apply(captor.capture());

        ApplyCoachingCompletedRewardCommand command = captor.getValue();
        assertThat(command.coachingId()).isEqualTo(event.coachingId());
        assertThat(command.userId()).isEqualTo(event.userId());
        assertThat(command.submissionId()).isEqualTo(event.submissionId());
        assertThat(command.problemId()).isEqualTo(event.problemId());
        assertThat(command.completedAt()).isEqualTo(event.completedAt());
    }

    @Test
    @DisplayName("중복 이벤트도 정상 처리로 종료한다")
    void consume_acceptsDuplicatedEvent() {
        CoachingCompletedEvent event = event();
        when(rewardService.apply(
                org.mockito.ArgumentMatchers.any(
                        ApplyCoachingCompletedRewardCommand.class
                )
        )).thenReturn(false);

        consumer.consume(event);

        verify(rewardService).apply(
                org.mockito.ArgumentMatchers.any(
                        ApplyCoachingCompletedRewardCommand.class
                )
        );
    }

    private CoachingCompletedEvent event() {
        return new CoachingCompletedEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                Instant.parse("2026-09-20T15:30:00Z")
        );
    }
}
