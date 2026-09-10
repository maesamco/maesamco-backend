package com.maesamco.content.dailyquiz.infrastructure.adapter;

import com.maesamco.content.global.response.SuccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * User Service의 Feign Client
 */
@FeignClient(
        name = "user-service",
        path = "/internal/v1",
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
