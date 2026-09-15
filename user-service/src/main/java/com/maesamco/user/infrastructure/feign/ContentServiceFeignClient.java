package com.maesamco.user.infrastructure.feign;

import com.maesamco.user.global.response.SuccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Content Service의 내부 개념 검증 API를 호출하는 Feign Client입니다.
 */
@FeignClient(
        name = "content-service",
        path = "/internal/v1",
        configuration = ContentServiceFeignConfig.class
)
public interface ContentServiceFeignClient {

    /**
     * 개념 ID 목록의 존재 여부와 활성 상태를 일괄 검증합니다.
     *
     * @param request 검증할 개념 ID 목록
     * @return 개념 검증 결과
     */
    @PostMapping("/concepts/validate")
    SuccessResponse<ConceptValidationResponse> validateConcepts(
            @RequestBody ConceptValidationRequest request
    );
}
