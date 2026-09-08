package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.application.port.ContentServicePort;
import com.maesamco.coaching.application.port.ProblemSnapshot;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ContentServiceAdapter implements ContentServicePort {

    private final ContentServiceFeignClient feignClient;

    public ContentServiceAdapter(ContentServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    /**
     * JudgeServiceAdapter.getSubmission()과 동일한 이유(팀 컨벤션 2절) — 모든
     * FeignAdapter 메서드에 CircuitBreaker를 적용한다. 서킷이 열려도(Content Service
     * 장애) 빈 값으로 조용히 넘기지 않고 재시도 가능한 실패(FEIGN_CLIENT_ERROR)로
     * 명확히 응답한다 — getProblemFallback() 참고.
     */
    @Override
    @CircuitBreaker(name = "content-service", fallbackMethod = "getProblemFallback")
    public ProblemSnapshot getProblem(UUID problemId) {
        try {
            ProblemDetailResponse data = feignClient.getProblem(problemId).data();
            return new ProblemSnapshot(data.id(), data.description(), data.conceptTags());
        } catch (FeignException.NotFound e) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        } catch (FeignException e) {
            throw new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR);
        }
    }

    /**
     * JudgeServiceAdapter.getSubmissionFallback()과 동일한 이유 — getProblem()이 이미
     * BusinessException으로 분류해서 던진 경우는 그대로 다시 던지고, 서킷이 열려 호출
     * 자체가 차단된 경우(CallNotPermittedException 등)만 FEIGN_CLIENT_ERROR로 변환한다.
     */
    @SuppressWarnings("unused")
    ProblemSnapshot getProblemFallback(UUID problemId, Throwable t) {
        if (t instanceof BusinessException businessException) {
            throw businessException;
        }
        throw new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR);
    }
}
