package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.global.response.SuccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "judge-service", path = InternalApiPrefix.INTERNAL_API_PREFIX, configuration = JudgeServiceFeignConfig.class)
public interface JudgeServiceFeignClient {

    @GetMapping("/submissions/{submissionId}")
    SuccessResponse<SubmissionDetailResponse> getSubmission(@PathVariable UUID submissionId);
}
