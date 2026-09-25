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
        path = ContentServiceFeignClient.INTERNAL_API_PREFIX,
        configuration = ContentServiceFeignConfig.class
)
public interface ContentServiceFeignClient {

    /** 이 클라이언트가 호출하는 Content Service 내부 API의 공통 prefix. HMAC 서명(ContentServiceFeignConfig)에도 쓴다. */
    String INTERNAL_API_PREFIX = "/internal/v1";

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
