package com.maesamco.content.infrastructure.messaging.consumer;

import com.maesamco.content.application.command.ProblemProgressSyncCommand;
import com.maesamco.content.application.command_service.ProblemProgressCommandService;
import com.maesamco.content.infrastructure.messaging.event.SubmissionJudgedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubmissionJudgedKafkaConsumerTest {

    @Mock
    private ProblemProgressCommandService problemProgressCommandService;

    private SubmissionJudgedKafkaConsumer submissionJudgedKafkaConsumer;

    @BeforeEach
    void setUp() {
        submissionJudgedKafkaConsumer = new SubmissionJudgedKafkaConsumer(
                problemProgressCommandService
        );
    }

    @Test
    @DisplayName("SubmissionJudged 이벤트를 ProblemProgressSyncCommand로 변환하여 동기화한다")
    void consume_submissionJudgedEvent_syncsProblemProgress() {
        // given
        UUID submissionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();
        int attemptNo = 3;
        String status = "COMPLETED";
        String result = "CORRECT";
        Instant judgedAt = Instant.parse("2026-09-21T00:00:00Z");

        SubmissionJudgedEvent event = new SubmissionJudgedEvent(
                submissionId,
                userId,
                problemId,
                problemVersionId,
                attemptNo,
                status,
                result,
                judgedAt
        );

        // when
        submissionJudgedKafkaConsumer.consume(event);

        // then
        ArgumentCaptor<ProblemProgressSyncCommand> commandCaptor =
                ArgumentCaptor.forClass(ProblemProgressSyncCommand.class);

        verify(problemProgressCommandService).sync(commandCaptor.capture());

        ProblemProgressSyncCommand command = commandCaptor.getValue();

        assertThat(command.submissionId()).isEqualTo(submissionId);
        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.problemId()).isEqualTo(problemId);
        assertThat(command.problemVersionId()).isEqualTo(problemVersionId);
        assertThat(command.attemptNo()).isEqualTo(attemptNo);
        assertThat(command.status()).isEqualTo(status);
        assertThat(command.result()).isEqualTo(result);
        assertThat(command.judgedAt()).isEqualTo(judgedAt);
    }

    @Test
    @DisplayName("ProblemProgress 동기화 중 예외가 발생하면 Consumer가 예외를 그대로 전파한다")
    void consume_syncFails_propagatesException() {
        // given
        SubmissionJudgedEvent event = new SubmissionJudgedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "COMPLETED",
                "CORRECT",
                Instant.parse("2026-09-21T00:00:00Z")
        );

        RuntimeException exception = new RuntimeException("sync failed");

        doThrow(exception)
                .when(problemProgressCommandService)
                .sync(any(ProblemProgressSyncCommand.class));

        // when & then
        assertThatThrownBy(() -> submissionJudgedKafkaConsumer.consume(event))
                .isSameAs(exception);
    }
}