package com.maesamco.content.problem.application.service;

import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import com.maesamco.content.problem.domain.entity.ProblemVersion;
import com.maesamco.content.problem.domain.entity.TestCase;
import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemEventOutboxStatus;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.problem.domain.enums.TimerPolicy;
import com.maesamco.content.problem.domain.repository.ProblemEventOutboxRepository;
import com.maesamco.content.problem.domain.repository.ProblemVersionRepository;
import com.maesamco.content.problem.domain.repository.TestCaseRepository;
import com.maesamco.content.problem.infrastructure.messaging.event.ProblemPublishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemPublicationServiceTest {

    @Mock
    private ProblemFinder problemFinder;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private ProblemVersionRepository problemVersionRepository;

    @Mock
    private ProblemEventOutboxRepository problemEventOutboxRepository;

    @Mock
    private JsonMapper jsonMapper;

    private ProblemPublicationService problemPublicationService;

    @BeforeEach
    void setUp() {
        problemPublicationService =
                new ProblemPublicationService(
                        problemFinder,
                        testCaseRepository,
                        problemVersionRepository,
                        problemEventOutboxRepository,
                        jsonMapper
                );
    }

    @Test
    @DisplayName(
            "발행 승인 시 문제를 PUBLISHED로 전환하고 "
                    + "ProblemVersion과 Outbox를 생성한다"
    )
    void approvePublicationCreatesVersionAndOutbox() {
        // given
        UUID problemId = UUID.randomUUID();
        UUID firstTestCaseId = UUID.randomUUID();
        UUID secondTestCaseId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();

        Problem problem =
                Problem.create(
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
                        2,
                        256,
                        TimerPolicy.values()[0],
                        ProblemSource.values()[0],
                        ProblemStatus.REVIEW_PENDING,
                        3
                );

        TestCase firstTestCase =
                TestCase.create(
                        problemId,
                        true,
                        "1 2",
                        "3",
                        1
                );

        TestCase secondTestCase =
                TestCase.create(
                        problemId,
                        false,
                        "10 20",
                        "30",
                        2
                );

        ReflectionTestUtils.setField(
                firstTestCase,
                "id",
                firstTestCaseId
        );

        ReflectionTestUtils.setField(
                secondTestCase,
                "id",
                secondTestCaseId
        );

        when(
                problemFinder.getProblem(problemId)
        ).thenReturn(problem);

        when(
                testCaseRepository
                        .findAllByProblemIdOrderByDisplayOrderAsc(
                                problemId
                        )
        ).thenReturn(
                List.of(
                        firstTestCase,
                        secondTestCase
                )
        );

        /*
         * 실제 JPA 환경에서는 GenerationType.UUID가
         * save 시점에 ProblemVersion ID를 생성합니다.
         *
         * 단위 테스트에서는 Repository가 Mock이므로
         * JPA의 UUID 생성 동작을 대신 재현합니다.
         */
        when(
                problemVersionRepository.save(
                        any(ProblemVersion.class)
                )
        ).thenAnswer(invocation -> {
            ProblemVersion problemVersion =
                    invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    problemVersion,
                    "id",
                    problemVersionId
            );

            return problemVersion;
        });

        String serializedPayload =
                """
                {
                  "eventType": "PROBLEM_PUBLISHED"
                }
                """;

        when(
                jsonMapper.writeValueAsString(
                        any(ProblemPublishedEvent.class)
                )
        ).thenReturn(serializedPayload);

        // when
        problemPublicationService.approvePublication(
                problemId
        );

        // then
        assertThat(
                problem.getProblemStatus()
        ).isEqualTo(
                ProblemStatus.PUBLISHED
        );

        ArgumentCaptor<ProblemVersion> problemVersionCaptor =
                ArgumentCaptor.forClass(
                        ProblemVersion.class
                );

        verify(
                problemVersionRepository
        ).save(
                problemVersionCaptor.capture()
        );

        ProblemVersion savedProblemVersion =
                problemVersionCaptor.getValue();

        assertThat(
                savedProblemVersion.getId()
        ).isEqualTo(
                problemVersionId
        );

        assertThat(
                savedProblemVersion.getProblemId()
        ).isEqualTo(
                problemId
        );

        assertThat(
                savedProblemVersion.getVersionNo()
        ).isEqualTo(
                3
        );

        assertThat(
                savedProblemVersion.getPublishedAt()
        ).isNotNull();

        assertThat(
                savedProblemVersion
                        .getContentSnapshot()
                        .testCases()
        ).hasSize(
                2
        );

        assertThat(
                savedProblemVersion
                        .getContentSnapshot()
                        .testCases()
                        .get(0)
                        .testCaseId()
        ).isEqualTo(
                firstTestCaseId
        );

        assertThat(
                savedProblemVersion
                        .getContentSnapshot()
                        .testCases()
                        .get(0)
                        .displayOrder()
        ).isEqualTo(
                1
        );

        assertThat(
                savedProblemVersion
                        .getContentSnapshot()
                        .testCases()
                        .get(1)
                        .testCaseId()
        ).isEqualTo(
                secondTestCaseId
        );

        assertThat(
                savedProblemVersion
                        .getContentSnapshot()
                        .testCases()
                        .get(1)
                        .displayOrder()
        ).isEqualTo(
                2
        );

        ArgumentCaptor<ProblemPublishedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        ProblemPublishedEvent.class
                );

        verify(
                jsonMapper
        ).writeValueAsString(
                eventCaptor.capture()
        );

        ProblemPublishedEvent event =
                eventCaptor.getValue();

        assertThat(
                event.eventId()
        ).isNotNull();

        assertThat(
                event.eventType()
        ).isEqualTo(
                ProblemPublishedEvent.EVENT_TYPE
        );

        assertThat(
                event.eventVersion()
        ).isEqualTo(
                ProblemPublishedEvent.EVENT_VERSION
        );

        assertThat(
                event.problemId()
        ).isEqualTo(
                problemId
        );

        assertThat(
                event.problemVersionId()
        ).isEqualTo(
                problemVersionId
        );

        assertThat(
                event.versionNo()
        ).isEqualTo(
                3
        );

        assertThat(
                event.language()
        ).isEqualTo(
                ProgrammingLanguage.JAVA.name()
        );

        assertThat(
                event.timeLimit()
        ).isEqualTo(
                2000
        );

        assertThat(
                event.memoryLimit()
        ).isEqualTo(
                256
        );

        assertThat(
                event.testCases()
        ).hasSize(
                2
        );

        ArgumentCaptor<ProblemEventOutbox> outboxCaptor =
                ArgumentCaptor.forClass(
                        ProblemEventOutbox.class
                );

        verify(
                problemEventOutboxRepository
        ).save(
                outboxCaptor.capture()
        );

        ProblemEventOutbox savedOutbox =
                outboxCaptor.getValue();

        assertThat(
                savedOutbox.getEventId()
        ).isEqualTo(
                event.eventId()
        );

        assertThat(
                savedOutbox.getAggregateId()
        ).isEqualTo(
                problemId
        );

        assertThat(
                savedOutbox.getEventType()
        ).isEqualTo(
                ProblemPublishedEvent.EVENT_TYPE
        );

        assertThat(
                savedOutbox.getEventVersion()
        ).isEqualTo(
                ProblemPublishedEvent.EVENT_VERSION
        );

        assertThat(
                savedOutbox.getPayload()
        ).isEqualTo(
                serializedPayload
        );

        assertThat(
                savedOutbox.getStatus()
        ).isEqualTo(
                ProblemEventOutboxStatus.PENDING
        );

        assertThat(
                savedOutbox.getRetryCount()
        ).isZero();

        assertThat(
                savedOutbox.getPublishedAt()
        ).isNull();

        assertThat(
                savedOutbox.getOccurredAt()
        ).isEqualTo(
                event.occurredAt()
        );

        assertThat(
                savedProblemVersion.getPublishedAt()
        ).isEqualTo(
                event.publishedAt()
        );
    }
}
