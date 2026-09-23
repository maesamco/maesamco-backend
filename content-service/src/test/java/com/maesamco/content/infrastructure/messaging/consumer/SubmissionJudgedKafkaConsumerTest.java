package com.maesamco.content.infrastructure.messaging.consumer;

import com.maesamco.content.application.command.ProblemProgressSyncCommand;
import com.maesamco.content.application.command_service.ProblemProgressCommandService;
import com.maesamco.content.infrastructure.messaging.event.SubmissionJudgedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubmissionJudgedKafkaConsumerTest {

    @Mock
    private ProblemProgressCommandService problemProgressCommandService;

    @InjectMocks
    private SubmissionJudgedKafkaConsumer submissionJudgedKafkaConsumer;

    @Test
    @DisplayName("SubmissionJudgedEvent를 수신하면 ProblemProgress 동기화 Command로 변환한다")
    void consume_convertsEventToCommand() {
        // given
        UUID submissionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();

        int attemptNo = 3;
        String status = "COMPLETED";
        String result = "CORRECT";
        Instant judgedAt = Instant.parse("2026-09-23T03:00:00Z");

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

        ArgumentCaptor<ProblemProgressSyncCommand> captor =
                ArgumentCaptor.forClass(ProblemProgressSyncCommand.class);

        // when
        submissionJudgedKafkaConsumer.consume(event);

        // then
        verify(problemProgressCommandService)
                .sync(captor.capture());

        ProblemProgressSyncCommand command = captor.getValue();

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
    @DisplayName("SubmissionJudgedEvent 한 건을 수신하면 ProblemProgressCommandService를 한 번 호출한다")
    void consume_callsProblemProgressCommandServiceOnce() {
        // given
        SubmissionJudgedEvent event = new SubmissionJudgedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "COMPLETED",
                "WRONG",
                Instant.parse("2026-09-23T03:00:00Z")
        );

        // when
        submissionJudgedKafkaConsumer.consume(event);

        // then
        verify(problemProgressCommandService, times(1))
                .sync(
                        org.mockito.ArgumentMatchers.any(
                                ProblemProgressSyncCommand.class
                        )
                );
    }

    @Test
    @DisplayName("Consumer의 consume 메서드는 String이 아닌 SubmissionJudgedEvent를 직접 수신한다")
    void consume_acceptsTypedSubmissionJudgedEvent() throws NoSuchMethodException {
        // given
        Method consumeMethod =
                SubmissionJudgedKafkaConsumer.class.getDeclaredMethod(
                        "consume",
                        SubmissionJudgedEvent.class
                );

        // when
        Class<?> parameterType =
                consumeMethod.getParameterTypes()[0];

        // then
        assertThat(parameterType)
                .isEqualTo(SubmissionJudgedEvent.class);
    }

    @Test
    @DisplayName("Consumer는 JSON 파싱을 위한 ObjectMapper를 직접 의존하지 않는다")
    void consumer_doesNotDependOnObjectMapper() {
        // given & when
        boolean hasObjectMapperDependency =
                Arrays.stream(
                                SubmissionJudgedKafkaConsumer.class
                                        .getDeclaredFields()
                        )
                        .anyMatch(field ->
                                field.getType()
                                        .getSimpleName()
                                        .equals("ObjectMapper")
                        );

        // then
        assertThat(hasObjectMapperDependency)
                .isFalse();
    }
}