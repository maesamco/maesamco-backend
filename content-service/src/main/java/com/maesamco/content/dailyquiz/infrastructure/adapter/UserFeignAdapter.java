package com.maesamco.content.dailyquiz.infrastructure.adapter;

import com.maesamco.content.dailyquiz.application.port.UserInterestConceptPort;
import com.maesamco.content.global.response.SuccessResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * User Service의 사용자 정보 응답을 Daily Quiz가 사용하는 관심 개념 목록으로 변환
 */
@Slf4j
@Component
public class UserFeignAdapter implements UserInterestConceptPort {

    private final UserServiceFeignClient feignClient;

    public UserFeignAdapter(UserServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    @CircuitBreaker(name = "user-service", fallbackMethod = "getInterestConceptIdsFallback")
    public List<UUID> getInterestConceptIds(UUID userId) {
        SuccessResponse<UserInterestConceptResponse> response = feignClient.getUser(userId);

        if (response == null || response.data() == null) {
            return List.of();
        }

        return response.data().interestConceptIds();
    }

    @SuppressWarnings("unused")
    List<UUID> getInterestConceptIdsFallback(UUID userId, Throwable throwable) {
        log.warn("User Service 관심 개념 조회 실패. userId={}", userId, throwable);
        return List.of();
    }
}
