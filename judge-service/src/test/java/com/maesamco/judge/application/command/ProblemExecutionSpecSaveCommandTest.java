package com.maesamco.judge.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent.TestCaseItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProblemExecutionSpecSaveCommandTest {

    @Test
    @DisplayName("from() — 이벤트의 TestCaseItem을 필드값 그대로 ExecutionTestCase로 변환한다")
    void from_convertsTestCaseItemsToExecutionTestCases() {
        // given
        UUID testCaseId1 = UUID.randomUUID();
        UUID testCaseId2 = UUID.randomUUID();
        ProblemPublishedEvent event = new ProblemPublishedEvent(
                UUID.randomUUID(), "ProblemPublished", 1, Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), "JAVA", "public class Main {}",
                List.of(
                        new TestCaseItem(testCaseId1, true, "3 5", "8", 1),
                        new TestCaseItem(testCaseId2, false, "1 1", "2", 2)
                ),
                2000, 256, Instant.now()
        );

        // when
        ProblemExecutionSpecSaveCommand command = ProblemExecutionSpecSaveCommand.from(event);

        // then
        assertThat(command.testCases()).containsExactly(
                new ExecutionTestCase(testCaseId1, true, "3 5", "8", 1),
                new ExecutionTestCase(testCaseId2, false, "1 1", "2", 2)
        );
    }
}