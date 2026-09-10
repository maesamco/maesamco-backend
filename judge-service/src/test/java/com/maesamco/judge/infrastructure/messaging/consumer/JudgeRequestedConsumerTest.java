package com.maesamco.judge.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.facade.JudgeExecutionFacade;
import com.maesamco.judge.infrastructure.messaging.consumer.JudgeRequestedConsumer.UnsupportedJudgeRequestedEventVersionException;
import com.maesamco.judge.infrastructure.messaging.event.JudgeRequestedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudgeRequestedConsumerTest {

    @Mock
    private JudgeExecutionFacade judgeExecutionFacade;

    @InjectMocks
    private JudgeRequestedConsumer judgeRequestedConsumer;

    private JudgeRequestedEvent eventWithVersion(int eventVersion) {
        return new JudgeRequestedEvent(
                UUID.randomUUID(),
                "JudgeRequested",
                eventVersion,
                Instant.now(),
                UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("지원하지 않는 eventVersion이면 전용 예외를 던지고 Facade는 호출하지 않는다")
    void throwsWhenEventVersionUnsupported() {
        // given
        JudgeRequestedEvent event = eventWithVersion(2);

        // when / then
        assertThatThrownBy(() -> judgeRequestedConsumer.consume(event))
                .isInstanceOf(UnsupportedJudgeRequestedEventVersionException.class);
        verify(judgeExecutionFacade, never()).execute(any());
    }

    @Test
    @DisplayName("지원하는 eventVersion(1)이면 예외 없이 Facade에 submissionId를 위임한다")
    void delegatesToFacadeWhenEventVersionSupported() {
        // given
        JudgeRequestedEvent event = eventWithVersion(1);

        // when / then
        assertThatCode(() -> judgeRequestedConsumer.consume(event))
                .doesNotThrowAnyException();
        verify(judgeExecutionFacade).execute(event.submissionId());
    }
}