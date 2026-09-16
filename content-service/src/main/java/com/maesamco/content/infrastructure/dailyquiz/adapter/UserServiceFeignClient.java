package com.maesamco.content.infrastructure.dailyquiz.adapter;

import com.maesamco.content.global.response.SuccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * User Service의 Feign Client
 *
 * ⚠️ path는 InternalApiPrefix.INTERNAL_API_PREFIX 상수를 참조한다(리터럴 직접 기입 금지) —
 * UserServiceFeignConfig의 HmacSigningFeignInterceptor가 서명할 때 쓰는 basePath와
 * 반드시 같은 값이어야 하며, 상수 하나로 묶어야 둘이 따로 놀다 어긋나는 걸 막는다
 * (이슈 #163, PR #162 설계 그대로 적용).
 */
@FeignClient(
        name = "user-service",
        path = InternalApiPrefix.INTERNAL_API_PREFIX,
        configuration = UserServiceFeignConfig.class
)
public interface UserServiceFeignClient {

    @GetMapping("/users/{userId}")
    SuccessResponse<UserInterestConceptResponse> getUser(@PathVariable("userId") UUID userId);

    @GetMapping("/users/quiz-targets")
    SuccessResponse<UserQuizTargetPageResponse> getQuizTargets(
            @RequestParam(value = "cursor", required = false) UUID cursor,
            @RequestParam("size") int size
    );
}