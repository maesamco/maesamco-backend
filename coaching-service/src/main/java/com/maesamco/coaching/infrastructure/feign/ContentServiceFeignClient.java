package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.global.response.SuccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "content-service", path = InternalApiPrefix.INTERNAL_API_PREFIX, configuration = ContentServiceFeignConfig.class)
public interface ContentServiceFeignClient {

    @GetMapping("/problems/{problemId}")
    SuccessResponse<ProblemDetailResponse> getProblem(@PathVariable UUID problemId);
}
