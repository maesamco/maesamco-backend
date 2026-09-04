package com.maesamco.judge.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.service.ProblemExecutionSpecService;
import com.maesamco.judge.infrastructure.messaging.consumer.ProblemPublishedConsumer.UnsupportedProblemPublishedEventVersionException;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent.TestCaseItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProblemPublishedConsumerTest {

    @Mock
    private ProblemExecutionSpecService problemExecutionSpecService;

    @InjectMocks
    private ProblemPublishedConsumer problemPublishedConsumer;

    private ProblemPublishedEvent eventWithVersion(int eventVersion) {
        return new ProblemPublishedEvent(
                UUID.randomUUID(),
                "ProblemPublished",
                eventVersion,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "JAVA",
                "public class Main {}",
                List.of(new TestCaseItem(UUID.randomUUID(), true, "3\n1 2 3", "6", 1)),
                1000,
                128,
                Instant.now()
        );
    }

    @Test
    @DisplayName("지원하지 않는 eventVersion이면 전용 예외를 던지고 서비스는 호출하지 않는다")
    void throwsWhenEventVersionUnsupported() {
        // given
        ProblemPublishedEvent event = eventWithVersion(2);

        // when / then
        assertThatThrownBy(() -> problemPublishedConsumer.consume(event))
                .isInstanceOf(UnsupportedProblemPublishedEventVersionException.class);
        verify(problemExecutionSpecService, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("지원하는 eventVersion(1)이면 예외 없이 서비스에 위임한다")
    void delegatesToServiceWhenEventVersionSupported() {
        // given
        ProblemPublishedEvent event = eventWithVersion(1);
        willDoNothing().given(problemExecutionSpecService).saveIfAbsent(event);

        // when / then
        assertThatCode(() -> problemPublishedConsumer.consume(event))
                .doesNotThrowAnyException();
        verify(problemExecutionSpecService).saveIfAbsent(event);
    }
}
