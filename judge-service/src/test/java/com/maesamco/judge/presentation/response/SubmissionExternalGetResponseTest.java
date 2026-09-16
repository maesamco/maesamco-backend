package com.maesamco.judge.presentation.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.maesamco.judge.application.result.SubmissionExternalGetResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.maesamco.judge.domain.entity.SubmissionResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubmissionExternalGetResponseTest {

    @Test
    @DisplayName("from()은 SubmissionExternalGetResult의 모든 필드를 빠짐없이 옮긴다")
    void fromCopiesAllFields() {
        SubmissionExternalGetResult.TestResultItem testResultItem =
                new SubmissionExternalGetResult.TestResultItem(UUID.randomUUID(), true, true, "8");
        SubmissionExternalGetResult result = new SubmissionExternalGetResult(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2,
                SubmissionStatus.COMPLETED, SubmissionResult.CORRECT, null,
                List.of(testResultItem),
                120, 15360, Instant.now(), Instant.now()
        );

        SubmissionExternalGetResponse response = SubmissionExternalGetResponse.from(result);

        assertThat(response.submissionId()).isEqualTo(result.submissionId());
        assertThat(response.problemId()).isEqualTo(result.problemId());
        assertThat(response.problemVersionId()).isEqualTo(result.problemVersionId());
        assertThat(response.attemptNo()).isEqualTo(result.attemptNo());
        assertThat(response.status()).isEqualTo(result.status());
        assertThat(response.result()).isEqualTo(result.result());
        assertThat(response.failureCode()).isEqualTo(result.failureCode());
        assertThat(response.executionTimeMs()).isEqualTo(result.executionTimeMs());
        assertThat(response.memoryUsedKb()).isEqualTo(result.memoryUsedKb());
        assertThat(response.submittedAt()).isEqualTo(result.submittedAt());
        assertThat(response.judgedAt()).isEqualTo(result.judgedAt());

        assertThat(response.testResults()).hasSize(1);
        SubmissionExternalGetResponse.TestResultItem item = response.testResults().get(0);
        assertThat(item.testCaseId()).isEqualTo(testResultItem.testCaseId());
        assertThat(item.isPublic()).isEqualTo(testResultItem.isPublic());
        assertThat(item.passed()).isEqualTo(testResultItem.passed());
        assertThat(item.actualOutput()).isEqualTo(testResultItem.actualOutput());
    }

    @Test
    @DisplayName("testResults가 비어있으면 그대로 빈 리스트로 옮긴다")
    void fromHandlesEmptyTestResults() {
        SubmissionExternalGetResult result = new SubmissionExternalGetResult(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                SubmissionStatus.RUNNING, null, null,
                List.of(),
                null, null, Instant.now(), null
        );

        SubmissionExternalGetResponse response = SubmissionExternalGetResponse.from(result);

        assertThat(response.testResults()).isEmpty();
        assertThat(response.result()).isNull();
        assertThat(response.failureCode()).isNull();
    }
}