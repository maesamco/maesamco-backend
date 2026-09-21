package com.maesamco.content.infrastructure.adapter.event;

import com.maesamco.content.application.port.ProblemPublishedEventData;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.ProblemVersionTestCaseItem;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import com.maesamco.content.infrastructure.messaging.event.ProblemPublishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemPublishedEventAdapterTest {

    @Mock
    private ProblemEventOutboxRepository problemEventOutboxRepository;

    @Mock
    private JsonMapper jsonMapper;

    private ProblemPublishedEventAdapter problemPublishedEventAdapter;

    @BeforeEach
    void setUp() {
        problemPublishedEventAdapter = new ProblemPublishedEventAdapter(problemEventOutboxRepository, jsonMapper);
    }

    @Test
    @DisplayName("ProblemPublishedEventData를 이벤트로 직렬화하고 PENDING Outbox를 저장한다")
    void record_serializesEventAndSavesPendingOutbox() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();
        UUID testCaseId = UUID.randomUUID();

        Instant occurredAt = Instant.parse("2026-09-21T01:00:00Z");
        Instant publishedAt = Instant.parse("2026-09-21T00:59:00Z");

        Problem problem = Problem.create(
                "두 수 더하기",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수를 입력받아 합을 반환하세요.",
                """
                public class Solution {
                    public int solution(int a, int b) {
                        return 0;
                    }
                }
                """,
                RunningTimeLimit.SECOND_2,
                RunningMemoryLimit.MB_256,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.PUBLISHED
        );

        ReflectionTestUtils.setField(problem, "id", problemId);
        ReflectionTestUtils.setField(problem, "currentVersionNo", 3);

        ProblemVersionTestCaseItem testCase = new ProblemVersionTestCaseItem(
                testCaseId,
                true,
                "1 2",
                "3",
                1
        );

        ProblemVersion problemVersion = ProblemVersion.createPublished(
                problem,
                List.of(testCase),
                publishedAt
        );

        ReflectionTestUtils.setField(problemVersion, "id", problemVersionId);

        ProblemPublishedEventData eventData = new ProblemPublishedEventData(
                eventId,
                occurredAt,
                problemVersionId,
                problemVersion
        );

        String payload = """
                {
                  "eventType": "PROBLEM_PUBLISHED"
                }
                """;

        when(jsonMapper.writeValueAsString(any(ProblemPublishedEvent.class))).thenReturn(payload);

        // when
        problemPublishedEventAdapter.record(eventData);

        // then
        ArgumentCaptor<ProblemPublishedEvent> eventCaptor = ArgumentCaptor.forClass(ProblemPublishedEvent.class);
        verify(jsonMapper).writeValueAsString(eventCaptor.capture());

        ProblemPublishedEvent event = eventCaptor.getValue();

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo(ProblemPublishedEvent.EVENT_TYPE);
        assertThat(event.eventVersion()).isEqualTo(ProblemPublishedEvent.EVENT_VERSION);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
        assertThat(event.problemId()).isEqualTo(problemId);
        assertThat(event.problemVersionId()).isEqualTo(problemVersionId);
        assertThat(event.versionNo()).isEqualTo(3);
        assertThat(event.language()).isEqualTo(ProgrammingLanguage.JAVA.name());
        assertThat(event.starterCode()).contains("public class Solution");
        assertThat(event.timeLimit()).isEqualTo(2000);
        assertThat(event.memoryLimit()).isEqualTo(256);
        assertThat(event.publishedAt()).isEqualTo(publishedAt);

        assertThat(event.testCases()).hasSize(1);
        assertThat(event.testCases().get(0).testCaseId()).isEqualTo(testCaseId);
        assertThat(event.testCases().get(0).isPublic()).isTrue();
        assertThat(event.testCases().get(0).input()).isEqualTo("1 2");
        assertThat(event.testCases().get(0).expectedOutput()).isEqualTo("3");
        assertThat(event.testCases().get(0).displayOrder()).isEqualTo(1);

        ArgumentCaptor<ProblemEventOutbox> outboxCaptor = ArgumentCaptor.forClass(ProblemEventOutbox.class);
        verify(problemEventOutboxRepository).save(outboxCaptor.capture());

        ProblemEventOutbox outbox = outboxCaptor.getValue();

        assertThat(outbox.getEventId()).isEqualTo(eventId);
        assertThat(outbox.getAggregateId()).isEqualTo(problemId);
        assertThat(outbox.getEventType()).isEqualTo(ProblemPublishedEvent.EVENT_TYPE);
        assertThat(outbox.getEventVersion()).isEqualTo(ProblemPublishedEvent.EVENT_VERSION);
        assertThat(outbox.getPayload()).isEqualTo(payload);
        assertThat(outbox.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("ProblemPublishedEvent 직렬화에 실패하면 Outbox를 저장하지 않고 예외를 발생시킨다")
    void record_serializationFailure_throwsExceptionAndDoesNotSaveOutbox() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();
        UUID testCaseId = UUID.randomUUID();

        Instant occurredAt = Instant.parse("2026-09-21T01:00:00Z");

        Problem problem = Problem.create(
                "두 수 더하기",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수를 입력받아 합을 반환하세요.",
                "class Solution {}",
                RunningTimeLimit.SECOND_2,
                RunningMemoryLimit.MB_256,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.PUBLISHED
        );

        ReflectionTestUtils.setField(problem, "id", problemId);
        ReflectionTestUtils.setField(problem, "currentVersionNo", 3);

        ProblemVersionTestCaseItem testCase = new ProblemVersionTestCaseItem(
                testCaseId,
                true,
                "1 2",
                "3",
                1
        );

        ProblemVersion problemVersion = ProblemVersion.createPublished(
                problem,
                List.of(testCase),
                occurredAt
        );

        ReflectionTestUtils.setField(problemVersion, "id", problemVersionId);

        ProblemPublishedEventData eventData = new ProblemPublishedEventData(
                eventId,
                occurredAt,
                problemVersionId,
                problemVersion
        );

        JacksonException serializationException = mock(JacksonException.class);

        when(jsonMapper.writeValueAsString(any(ProblemPublishedEvent.class)))
                .thenThrow(serializationException);

        // when & then
        assertThatThrownBy(() -> problemPublishedEventAdapter.record(eventData))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ProblemPublished event serialization failed")
                .hasCause(serializationException);

        verifyNoInteractions(problemEventOutboxRepository);
    }
}