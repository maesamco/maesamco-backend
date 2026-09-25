package com.maesamco.content.infrastructure.dailyquiz.adapter;

import com.maesamco.content.application.dailyquiz.exception.DailyQuizUserLookupException;
import com.maesamco.content.application.dailyquiz.port.UserInterestConceptPort;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.SuccessResponse;
import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
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

        if (response == null || !response.success() || response.data() == null) {
            throw new DailyQuizUserLookupException(
                    "User Service 관심 개념 조회 응답이 올바르지 않습니다.");
        }

        return response.data().interestConceptIds();
    }

    @SuppressWarnings("unused")
    List<UUID> getInterestConceptIdsFallback(UUID userId, Throwable throwable) {
        if (throwable instanceof DailyQuizUserLookupException lookupException) {
            throw lookupException;
        }
        if (throwable instanceof BusinessException businessException) {
            throw businessException;
        }
        if (isUserSpecificFailure(throwable)) {
            throw new DailyQuizUserLookupException(
                    "User Service 사용자별 관심 개념 응답을 처리할 수 없습니다.", throwable);
        }

        log.error("User Service 관심 개념 조회 실패. userId={}", userId, throwable);
        throw new BusinessException(
                ErrorCode.FEIGN_CLIENT_ERROR,
                "User Service 관심 개념 조회 중 서비스 간 통신에 실패했습니다."
        );
    }

    private boolean isUserSpecificFailure(Throwable throwable) {
        if (throwable instanceof DecodeException) {
            return true;
        }
        if (throwable instanceof RetryableException retryableException
                && retryableException.status() < 0
                && hasSocketTimeoutCause(throwable)) {
            return true;
        }
        if (throwable instanceof FeignException feignException) {
            int status = feignException.status();
            // 인증과 요청 제한은 특정 사용자 데이터가 아니라 서비스 전체에 영향을 줍니다.
            return status >= 400 && status < 500
                    && status != 401 && status != 403 && status != 429;
        }
        return false;
    }

    private boolean hasSocketTimeoutCause(Throwable throwable) {
        for (Throwable cause = throwable.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
