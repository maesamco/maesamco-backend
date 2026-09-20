package com.maesamco.user.infrastructure.messaging.consumer;

import com.maesamco.user.application.service.ApplyFirstCorrectRewardCommand;
import com.maesamco.user.application.service.ApplyFirstCorrectRewardService;
import com.maesamco.user.infrastructure.messaging.event.SubmissionJudgedEvent;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionJudgedConsumerTest {

    private static final long RECEIVED_TIMESTAMP = 1789918200000L;

    @Mock
    private ApplyFirstCorrectRewardService rewardService;

    @InjectMocks
    private SubmissionJudgedConsumer consumer;

    @Test
    @DisplayName("정답 이벤트를 최초 정답 보상 명령으로 변환한다")
    void consume_delegatesCorrectResult() {
        SubmissionJudgedEvent event = event("COMPLETED", "CORRECT");
        when(rewardService.apply(any(ApplyFirstCorrectRewardCommand.class)))
                .thenReturn(true);

        consumer.consume(event, RECEIVED_TIMESTAMP);

        ArgumentCaptor<ApplyFirstCorrectRewardCommand> captor =
                ArgumentCaptor.forClass(
                        ApplyFirstCorrectRewardCommand.class
                );
        verify(rewardService).apply(captor.capture());

        ApplyFirstCorrectRewardCommand command = captor.getValue();
        assertThat(command.submissionId()).isEqualTo(event.submissionId());
        assertThat(command.userId()).isEqualTo(event.userId());
        assertThat(command.problemId()).isEqualTo(event.problemId());
        assertThat(command.judgedAt())
                .isEqualTo(Instant.ofEpochMilli(RECEIVED_TIMESTAMP));
    }

    @Test
    @DisplayName("오답 이벤트에는 XP를 지급하지 않는다")
    void consume_skipsWrongResult() {
        consumer.consume(
                event("COMPLETED", "WRONG"),
                RECEIVED_TIMESTAMP
        );

        verify(rewardService, never())
                .apply(any(ApplyFirstCorrectRewardCommand.class));
    }

    private SubmissionJudgedEvent event(String status, String result) {
        return new SubmissionJudgedEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                status,
                result
        );
    }
}
