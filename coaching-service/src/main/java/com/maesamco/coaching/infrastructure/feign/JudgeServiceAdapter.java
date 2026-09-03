package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class JudgeServiceAdapter implements JudgeServicePort {

    private final JudgeServiceFeignClient feignClient;

    public JudgeServiceAdapter(JudgeServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    /**
     * 팀 컨벤션 2절 — 모든 FeignAdapter 메서드에 CircuitBreaker를 적용한다. 힌트 생성
     * 핵심 경로의 호출이라, 서킷이 열려도(Judge Service 장애) 빈 값으로 조용히 넘기지
     * 않고 재시도 가능한 실패(FEIGN_CLIENT_ERROR)로 명확히 응답한다 —
     * getSubmissionFallback() 참고.
     */
    @Override
    @CircuitBreaker(name = "judge-service", fallbackMethod = "getSubmissionFallback")
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

    /**
     * getSubmission()이 이미 BusinessException으로 분류해서 던진 경우(SUBMISSION_NOT_FOUND
     * 등)는 그대로 다시 던진다 — 서킷이 닫혀 있어 실제로 호출은 됐고, 원인은 이미 정확히
     * 분류돼 있다. 서킷이 열려 호출 자체가 차단된 경우(CallNotPermittedException 등)만
     * FEIGN_CLIENT_ERROR로 변환한다.
     */
    @SuppressWarnings("unused")
    SubmissionSnapshot getSubmissionFallback(UUID submissionId, Throwable t) {
        if (t instanceof BusinessException businessException) {
            throw businessException;
        }
        throw new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR);
    }
}
