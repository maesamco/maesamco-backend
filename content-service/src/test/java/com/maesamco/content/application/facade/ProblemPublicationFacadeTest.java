package com.maesamco.content.application.facade;

import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.application.finder.TestCaseFinder;
import com.maesamco.content.application.port.ProblemPublishedEventData;
import com.maesamco.content.application.port.ProblemPublishedEventPort;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.ProblemVersionSnapshot;
import com.maesamco.content.domain.entity.problem.ProblemVersionTestCaseItem;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemPublicationFacadeTest {

    @Mock
    private ProblemFinder problemFinder;

    @Mock
    private TestCaseFinder testCaseFinder;

    @Mock
    private ProblemVersionRepository problemVersionRepository;

    @Mock
    private ProblemPublishedEventPort problemPublishedEventPort;

    private ProblemPublicationFacade problemPublicationFacade;

    private UUID problemId;

    @BeforeEach
    void setUp() {
        problemPublicationFacade = new ProblemPublicationFacade(
                problemFinder,
                testCaseFinder,
                problemVersionRepository,
                problemPublishedEventPort
        );

        problemId = UUID.randomUUID();
    }

    @Test
    @DisplayName("발행 승인 시 문제를 PUBLISHED로 변경하고 ProblemVersion 저장 후 발행 이벤트를 기록한다")
    void approvePublication_success_createsVersionAndRecordsEvent() {
        // given
        UUID firstTestCaseId = UUID.randomUUID();
        UUID secondTestCaseId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();

        Problem problem = createReviewPendingProblem();

        ReflectionTestUtils.setField(problem, "id", problemId);
        ReflectionTestUtils.setField(problem, "currentVersionNo", 2);

        TestCase firstTestCase = TestCase.createByAdmin(
                problemId,
                "1 2",
                "3",
                true,
                1
        );

        TestCase secondTestCase = TestCase.createByAdmin(
                problemId,
                "10 20",
                "30",
                false,
                2
        );

        ReflectionTestUtils.setField(firstTestCase, "id", firstTestCaseId);
        ReflectionTestUtils.setField(secondTestCase, "id", secondTestCaseId);

        when(problemFinder.lockById(problemId)).thenReturn(problem);
        when(testCaseFinder.findApprovedTestCases(problemId)).thenReturn(List.of(firstTestCase, secondTestCase));
        when(problemVersionRepository.save(any(ProblemVersion.class))).thenAnswer(invocation -> {
            ProblemVersion problemVersion = invocation.getArgument(0);
            ReflectionTestUtils.setField(problemVersion, "id", problemVersionId);
            return problemVersion;
        });

        // when
        problemPublicationFacade.approvePublication(problemId);

        // then
        assertThat(problem.getProblemStatus()).isEqualTo(ProblemStatus.PUBLISHED);
        assertThat(problem.getCurrentVersionNo()).isEqualTo(3);

        ArgumentCaptor<ProblemVersion> versionCaptor = ArgumentCaptor.forClass(ProblemVersion.class);

        verify(problemVersionRepository).save(versionCaptor.capture());

        ProblemVersion savedVersion = versionCaptor.getValue();

        assertThat(savedVersion.getId()).isEqualTo(problemVersionId);
        assertThat(savedVersion.getProblemId()).isEqualTo(problemId);
        assertThat(savedVersion.getVersionNo()).isEqualTo(3);
        assertThat(savedVersion.getPublishedAt()).isNotNull();

        ProblemVersionSnapshot snapshot = savedVersion.toVersionSnapshot();

        assertThat(snapshot.title()).isEqualTo(problem.getTitle());
        assertThat(snapshot.language()).isEqualTo(problem.getLanguage());
        assertThat(snapshot.difficulty()).isEqualTo(problem.getDifficulty());
        assertThat(snapshot.type()).isEqualTo(problem.getType());
        assertThat(snapshot.description()).isEqualTo(problem.getDescription());
        assertThat(snapshot.starterCode()).isEqualTo(problem.getStarterCode());
        assertThat(snapshot.timerPolicy()).isEqualTo(problem.getTimerPolicy());
        assertThat(snapshot.source()).isEqualTo(problem.getSource());
        assertThat(snapshot.testCases()).hasSize(2);

        ProblemVersionTestCaseItem firstSnapshot = snapshot.testCases().get(0);

        assertThat(firstSnapshot.testCaseId()).isEqualTo(firstTestCaseId);
        assertThat(firstSnapshot.isPublic()).isTrue();
        assertThat(firstSnapshot.input()).isEqualTo("1 2");
        assertThat(firstSnapshot.expectedOutput()).isEqualTo("3");
        assertThat(firstSnapshot.displayOrder()).isEqualTo(1);

        ProblemVersionTestCaseItem secondSnapshot = snapshot.testCases().get(1);

        assertThat(secondSnapshot.testCaseId()).isEqualTo(secondTestCaseId);
        assertThat(secondSnapshot.isPublic()).isFalse();
        assertThat(secondSnapshot.input()).isEqualTo("10 20");
        assertThat(secondSnapshot.expectedOutput()).isEqualTo("30");
        assertThat(secondSnapshot.displayOrder()).isEqualTo(2);

        ArgumentCaptor<ProblemPublishedEventData> eventCaptor =
                ArgumentCaptor.forClass(ProblemPublishedEventData.class);

        verify(problemPublishedEventPort).record(eventCaptor.capture());

        ProblemPublishedEventData eventData = eventCaptor.getValue();

        assertThat(eventData.eventId()).isNotNull();
        assertThat(eventData.problemVersionId()).isEqualTo(problemVersionId);
        assertThat(eventData.problemVersion()).isSameAs(savedVersion);
        assertThat(eventData.occurredAt()).isEqualTo(savedVersion.getPublishedAt());

        verify(problemFinder).lockById(problemId);
        verify(testCaseFinder).findApprovedTestCases(problemId);

        InOrder inOrder = inOrder(problemVersionRepository, problemPublishedEventPort);

        inOrder.verify(problemVersionRepository).save(any(ProblemVersion.class));
        inOrder.verify(problemPublishedEventPort).record(any(ProblemPublishedEventData.class));
    }

    @Test
    @DisplayName("승인된 테스트케이스가 없으면 문제를 발행할 수 없다")
    void approvePublication_noApprovedTestCases_throwsException() {
        // given
        Problem problem = createReviewPendingProblem();

        ReflectionTestUtils.setField(problem, "id", problemId);

        when(problemFinder.lockById(problemId)).thenReturn(problem);
        when(testCaseFinder.findApprovedTestCases(problemId)).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> problemPublicationFacade.approvePublication(problemId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PROBLEM_PUBLICATION_TEST_CASE_REQUIRED);

        assertThat(problem.getProblemStatus()).isEqualTo(ProblemStatus.REVIEW_PENDING);

        verify(problemFinder).lockById(problemId);
        verify(testCaseFinder).findApprovedTestCases(problemId);
        verify(problemVersionRepository, never()).save(any(ProblemVersion.class));
        verifyNoInteractions(problemPublishedEventPort);
    }

    @Test
    @DisplayName("ProblemVersion 저장 후 ID가 생성되지 않으면 발행 이벤트를 기록하지 않는다")
    void approvePublication_versionIdNotGenerated_throwsException() {
        // given
        Problem problem = createReviewPendingProblem();

        ReflectionTestUtils.setField(problem, "id", problemId);

        TestCase testCase = createApprovedTestCase(problemId);

        when(problemFinder.lockById(problemId)).thenReturn(problem);
        when(testCaseFinder.findApprovedTestCases(problemId)).thenReturn(List.of(testCase));
        when(problemVersionRepository.save(any(ProblemVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when & then
        assertThatThrownBy(() -> problemPublicationFacade.approvePublication(problemId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ProblemVersion id was not generated");

        verify(problemVersionRepository).save(any(ProblemVersion.class));
        verifyNoInteractions(problemPublishedEventPort);
    }

    @Test
    @DisplayName("ProblemVersion 저장에 실패하면 발행 이벤트를 기록하지 않는다")
    void approvePublication_versionSaveFails_doesNotRecordEvent() {
        // given
        Problem problem = createReviewPendingProblem();

        ReflectionTestUtils.setField(problem, "id", problemId);

        TestCase testCase = createApprovedTestCase(problemId);

        when(problemFinder.lockById(problemId)).thenReturn(problem);
        when(testCaseFinder.findApprovedTestCases(problemId)).thenReturn(List.of(testCase));
        when(problemVersionRepository.save(any(ProblemVersion.class)))
                .thenThrow(new IllegalStateException("database error"));

        // when & then
        assertThatThrownBy(() -> problemPublicationFacade.approvePublication(problemId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database error");

        verify(problemVersionRepository).save(any(ProblemVersion.class));
        verifyNoInteractions(problemPublishedEventPort);
    }

    @Test
    @DisplayName("발행 이벤트 기록에 실패하면 예외를 전파한다")
    void approvePublication_eventRecordFails_propagatesException() {
        // given
        UUID problemVersionId = UUID.randomUUID();

        Problem problem = createReviewPendingProblem();

        ReflectionTestUtils.setField(problem, "id", problemId);

        TestCase testCase = createApprovedTestCase(problemId);

        when(problemFinder.lockById(problemId)).thenReturn(problem);
        when(testCaseFinder.findApprovedTestCases(problemId)).thenReturn(List.of(testCase));
        when(problemVersionRepository.save(any(ProblemVersion.class))).thenAnswer(invocation -> {
            ProblemVersion version = invocation.getArgument(0);
            ReflectionTestUtils.setField(version, "id", problemVersionId);
            return version;
        });

        doThrow(new IllegalStateException("outbox save failed"))
                .when(problemPublishedEventPort)
                .record(any(ProblemPublishedEventData.class));

        // when & then
        assertThatThrownBy(() -> problemPublicationFacade.approvePublication(problemId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox save failed");

        verify(problemVersionRepository).save(any(ProblemVersion.class));
        verify(problemPublishedEventPort).record(any(ProblemPublishedEventData.class));
    }

    private Problem createReviewPendingProblem() {
        return Problem.create(
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
                ProblemStatus.REVIEW_PENDING
        );
    }

    private TestCase createApprovedTestCase(UUID problemId) {
        TestCase testCase = TestCase.createByAdmin(
                problemId,
                "1 2",
                "3",
                true,
                1
        );

        ReflectionTestUtils.setField(testCase, "id", UUID.randomUUID());

        return testCase;
    }
}