package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class JudgeServiceAdapter implements JudgeServicePort {

    private final JudgeServiceFeignClient feignClient;

    public JudgeServiceAdapter(JudgeServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    public SubmissionSnapshot getSubmission(UUID submissionId) {
        try {
            SubmissionDetailResponse data = feignClient.getSubmission(submissionId).data();
            List<SubmissionSnapshot.FailedTest> failedTests = data.failedTestSummary().stream()
                    .map(f -> new SubmissionSnapshot.FailedTest(f.isPublic(), f.errorType()))
                    .toList();
            return new SubmissionSnapshot(
                    data.submissionId(), data.userId(), data.problemId(), data.code(),
                    data.result(), failedTests, data.attemptNo()
            );
        } catch (FeignException.NotFound e) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        } catch (FeignException e) {
            throw new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR);
        }
    }
}
