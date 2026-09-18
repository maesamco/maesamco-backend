package com.maesamco.judge.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.command.ExecutionValidationTestCase;
import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.application.port.JudgeExecutionResult;
import com.maesamco.judge.application.port.JudgeExecutionStatus;
import com.maesamco.judge.application.result.ExecutionValidationResult;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExecutionValidationFacadeTest {

    @Mock
    private JudgeExecutionPort judgeExecutionPort;

    @InjectMocks
    private ExecutionValidationFacade executionValidationFacade;

    @BeforeEach
    void setUpConfig() {
        // @Value 필드는 스프링 컨텍스트 없이는 주입 안 되니 직접 세팅.
        // pollIntervalMs=0으로 둬서 테스트가 실제 sleep으로 느려지지 않게 함.
        ReflectionTestUtils.setField(executionValidationFacade, "maxPollAttempts", 3);
        ReflectionTestUtils.setField(executionValidationFacade, "pollIntervalMs", 0L);
        ReflectionTestUtils.setField(executionValidationFacade, "maxConsecutiveFetchFailures", 2);
    }

    private ExecutionValidationTestCase testCase() {
        return new ExecutionValidationTestCase("3 5", "8", 2, 256);
    }

    private JudgeExecutionResult result(String token, JudgeExecutionStatus status, String stdout) {
        return new JudgeExecutionResult(token, status, stdout, null, null, null, null);
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("빈 testCases면 Judge0 호출 없이 빈 리스트를 반환한다")
        void returnsEmptyWhenTestCasesEmpty() {
            List<ExecutionValidationResult> results = executionValidationFacade.validate("code", List.of());

            assertThat(results).isEmpty();
            verify(judgeExecutionPort, never()).submitBatch(any());
        }

        @Test
        @DisplayName("정상 케이스 - 토큰이 바로 ACCEPTED로 끝나면 passed=true, timedOut=false")
        void returnsPassedWhenAccepted() {
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));
            given(judgeExecutionPort.fetchResults(anyList()))
                    .willReturn(List.of(result("token-1", JudgeExecutionStatus.ACCEPTED, "8")));

            List<ExecutionValidationResult> results =
                    executionValidationFacade.validate("code", List.of(testCase()));

            assertThat(results).hasSize(1);
            ExecutionValidationResult r = results.get(0);
            assertThat(r.testCaseIndex()).isZero();
            assertThat(r.passed()).isTrue();
            assertThat(r.timedOut()).isFalse();
            assertThat(r.stdout()).isEqualTo("8");
        }

        @Test
        @DisplayName("Judge0 응답 개수가 요청과 다르면 예외를 던진다")
        void throwsWhenTokenCountMismatch() {
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));

            assertThatThrownBy(() ->
                    executionValidationFacade.validate("code", List.of(testCase(), testCase())))
                    .isInstanceOf(IllegalStateException.class);
            verify(judgeExecutionPort, never()).fetchResults(any());
        }

        @Test
        @DisplayName("제출 자체가 실패한(토큰 null) 케이스는 timedOut=true로 표시되고, 나머지 토큰은 정상 polling된다")
        void marksNullTokenAsTimedOut() {
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(Arrays.asList(null, "token-2"));
            given(judgeExecutionPort.fetchResults(anyList()))
                    .willReturn(List.of(result("token-2", JudgeExecutionStatus.ACCEPTED, "2")));

            List<ExecutionValidationResult> results =
                    executionValidationFacade.validate("code", List.of(testCase(), testCase()));

            assertThat(results).hasSize(2);
            assertThat(results.get(0).timedOut()).isTrue();
            assertThat(results.get(0).passed()).isFalse();
            assertThat(results.get(1).passed()).isTrue();

            verify(judgeExecutionPort).fetchResults(List.of("token-2"));
        }

        @Test
        @DisplayName("maxPollAttempts를 다 채워도 계속 PROCESSING이면 timedOut=true로 마무리한다")
        void marksTimedOutWhenStillPendingAfterMaxAttempts() {
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));
            given(judgeExecutionPort.fetchResults(anyList()))
                    .willReturn(List.of(result("token-1", JudgeExecutionStatus.PROCESSING, null)));

            List<ExecutionValidationResult> results =
                    executionValidationFacade.validate("code", List.of(testCase()));

            assertThat(results.get(0).timedOut()).isTrue();
            assertThat(results.get(0).passed()).isFalse();
            // maxPollAttempts=3으로 세팅했으니 정확히 3번 호출됐는지도 확인
            verify(judgeExecutionPort, times(3)).fetchResults(any());
        }

        @Test
        @DisplayName("fetchResults가 빈 응답을 줘도 '끝났다'고 오판하지 않고 계속 polling한다")
        void doesNotMisjudgeEmptyResponseAsDone() {
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));
            given(judgeExecutionPort.fetchResults(anyList()))
                    .willReturn(List.of()); // 항상 빈 응답 → 끝까지 pending으로 취급돼야 함

            List<ExecutionValidationResult> results =
                    executionValidationFacade.validate("code", List.of(testCase()));

            assertThat(results.get(0).timedOut()).isTrue();
            verify(judgeExecutionPort, times(3)).fetchResults(any());
        }

        @Test
        @DisplayName("fetchResults가 연속으로 maxConsecutiveFetchFailures회 실패하면 예외를 던진다")
        void throwsWhenConsecutiveFailuresExceedLimit() {
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));
            given(judgeExecutionPort.fetchResults(anyList()))
                    .willThrow(new RuntimeException("Judge0 커넥션 오류"));

            assertThatThrownBy(() -> executionValidationFacade.validate("code", List.of(testCase())))
                    .isInstanceOf(IllegalStateException.class);
            // maxConsecutiveFetchFailures=2로 세팅했으니 딱 2번 실패 시도 후 던져야 함
            verify(judgeExecutionPort, times(2)).fetchResults(any());
        }

        @Test
        @DisplayName("연속 실패 도중 한 번이라도 성공하면 실패 카운트가 리셋되고 계속 진행된다")
        void resetsConsecutiveFailureCountOnSuccess() {
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));
            given(judgeExecutionPort.fetchResults(anyList()))
                    .willThrow(new RuntimeException("일시적 오류"))
                    .willReturn(List.of(result("token-1", JudgeExecutionStatus.ACCEPTED, "8")));

            List<ExecutionValidationResult> results =
                    executionValidationFacade.validate("code", List.of(testCase()));

            assertThat(results.get(0).passed()).isTrue();
            assertThat(results.get(0).timedOut()).isFalse();
        }
    }

    @Test
    @DisplayName("이전 polling에서 완료된 토큰이 이후 응답에 없어도 결과가 유실되지 않는다")
    void preservesPreviouslyCompletedTokenWhenOmittedFromLaterResponse() {
        given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-A", "token-B"));
        given(judgeExecutionPort.fetchResults(anyList()))
                .willReturn(List.of(
                        result("token-A", JudgeExecutionStatus.ACCEPTED, "8"),
                        result("token-B", JudgeExecutionStatus.PROCESSING, null)))
                .willReturn(List.of(
                        result("token-B", JudgeExecutionStatus.ACCEPTED, "2")));
        // token-A는 두 번째 응답에서 빠짐 — 이전에 확보한 ACCEPTED 결과가 유지돼야 함

        List<ExecutionValidationResult> results =
                executionValidationFacade.validate("code", List.of(testCase(), testCase()));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(0).timedOut()).isFalse();
        assertThat(results.get(1).passed()).isTrue();
        assertThat(results.get(1).timedOut()).isFalse();
    }

    @Test
    @DisplayName("polling 예산이 거의 소진된 시점에 fetch가 한 번 실패해도, 실패는 attempt 예산을 소진하지 않고 재시도된다")
    void fetchFailureNearBoundaryDoesNotConsumeAttemptBudget() {
        given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));
        given(judgeExecutionPort.fetchResults(anyList()))
                .willReturn(List.of(result("token-1", JudgeExecutionStatus.PROCESSING, null))) // 1차: 성공(진행중), attempt=1
                .willReturn(List.of(result("token-1", JudgeExecutionStatus.PROCESSING, null))) // 2차: 성공(진행중), attempt=2
                .willThrow(new RuntimeException("일시적 조회 실패")) // 3차: 실패 — attempt는 2로 유지돼야 함(예전 버그였다면 여기서 timedOut 확정)
                .willReturn(List.of(result("token-1", JudgeExecutionStatus.ACCEPTED, "8"))); // 재시도 성공, attempt=3

        List<ExecutionValidationResult> results =
                executionValidationFacade.validate("code", List.of(testCase()));

        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(0).timedOut()).isFalse();
        verify(judgeExecutionPort, times(4)).fetchResults(any());
    }
}