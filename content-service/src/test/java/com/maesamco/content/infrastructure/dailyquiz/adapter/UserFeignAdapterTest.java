package com.maesamco.content.infrastructure.dailyquiz.adapter;

import com.maesamco.content.application.dailyquiz.exception.DailyQuizUserLookupException;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.SuccessResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.codec.DecodeException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFeignAdapterTest {

    @Mock
    private UserServiceFeignClient feignClient;

    private UserFeignAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserFeignAdapter(feignClient);
    }

    @Test
    @DisplayName("User Service의 관심 개념 ID 목록을 반환한다")
    void getInterestConceptIds_returnsInterestConceptIds() {
        UUID userId = UUID.randomUUID();
        UUID firstConceptId = UUID.randomUUID();
        UUID secondConceptId = UUID.randomUUID();

        when(feignClient.getUser(userId))
                .thenReturn(SuccessResponse.success(
                        new UserInterestConceptResponse(
                                List.of(firstConceptId, secondConceptId)
                        )
                ));

        List<UUID> result = adapter.getInterestConceptIds(userId);

        assertThat(result).containsExactly(firstConceptId, secondConceptId);
    }

    @Test
    @DisplayName("관심 개념이 없는 사용자는 빈 목록을 반환한다")
    void getInterestConceptIds_returnsEmptyListWhenUserHasNoInterests() {
        UUID userId = UUID.randomUUID();

        when(feignClient.getUser(userId))
                .thenReturn(SuccessResponse.success(
                        new UserInterestConceptResponse(List.of())
                ));

        assertThat(adapter.getInterestConceptIds(userId)).isEmpty();
    }

    @Test
    @DisplayName("특정 사용자의 공통 응답이 null이면 사용자별 조회 오류로 처리한다")
    void getInterestConceptIds_rejectsNullResponse() {
        UUID userId = UUID.randomUUID();
        when(feignClient.getUser(userId)).thenReturn(null);

        assertUserLookupError(() -> adapter.getInterestConceptIds(userId));
    }

    @Test
    @DisplayName("특정 사용자의 실패 공통 응답이면 사용자별 조회 오류로 처리한다")
    void getInterestConceptIds_rejectsUnsuccessfulResponse() {
        UUID userId = UUID.randomUUID();
        when(feignClient.getUser(userId))
                .thenReturn(new SuccessResponse<>(
                        false,
                        new UserInterestConceptResponse(List.of(UUID.randomUUID()))
                ));

        assertUserLookupError(() -> adapter.getInterestConceptIds(userId));
    }

    @Test
    @DisplayName("특정 사용자의 응답 data가 없으면 사용자별 조회 오류로 처리한다")
    void getInterestConceptIds_rejectsMissingData() {
        UUID userId = UUID.randomUUID();
        when(feignClient.getUser(userId))
                .thenReturn(new SuccessResponse<>(true, null));

        assertUserLookupError(() -> adapter.getInterestConceptIds(userId));
    }

    @Test
    @DisplayName("사용자별 404 및 응답 디코딩 오류는 사용자별 조회 오류로 분류한다")
    void getInterestConceptIdsFallback_isolatesUserResponseFailures() {
        UUID userId = UUID.randomUUID();

        assertUserLookupError(() -> adapter.getInterestConceptIdsFallback(userId, feignError(404)));
        assertUserLookupError(() -> adapter.getInterestConceptIdsFallback(
                userId, new DecodeException(200, "invalid response", request())));
    }

    @Test
    @DisplayName("개별 사용자 응답 타임아웃은 사용자별 조회 오류로 분류한다")
    void getInterestConceptIdsFallback_isolatesUserTimeout() {
        UUID userId = UUID.randomUUID();
        RetryableException timeout = new RetryableException(
                -1, "Read timed out", Request.HttpMethod.GET,
                new SocketTimeoutException("Read timed out"), (Long) null, request());

        assertUserLookupError(() -> adapter.getInterestConceptIdsFallback(userId, timeout));
    }

    @Test
    @DisplayName("서버 및 인증 오류와 서킷 오픈은 공통 장애로 유지한다")
    void getInterestConceptIdsFallback_keepsSharedFailuresRetryable() {
        UUID userId = UUID.randomUUID();

        assertFeignClientError(() -> adapter.getInterestConceptIdsFallback(userId, feignError(503)));
        assertFeignClientError(() -> adapter.getInterestConceptIdsFallback(userId, feignError(401)));
        assertFeignClientError(() -> adapter.getInterestConceptIdsFallback(userId, feignError(429)));
        assertFeignClientError(() -> adapter.getInterestConceptIdsFallback(userId,
                new RetryableException(-1, "connection refused", Request.HttpMethod.GET,
                        new ConnectException("connection refused"), (Long) null, request())));
        assertFeignClientError(() -> adapter.getInterestConceptIdsFallback(userId,
                CallNotPermittedException.createCallNotPermittedException(
                        CircuitBreaker.ofDefaults("user-service"))));
    }

    @Test
    @DisplayName("통신 장애 fallback은 서비스 간 통신 오류로 변환한다")
    void getInterestConceptIdsFallback_wrapsCommunicationFailure() {
        assertFeignClientError(() -> adapter.getInterestConceptIdsFallback(
                UUID.randomUUID(),
                new IllegalStateException("User Service 연결 실패")
        ));
    }

    @Test
    @DisplayName("fallback에 전달된 비즈니스 예외는 원래 오류를 유지한다")
    void getInterestConceptIdsFallback_preservesBusinessException() {
        BusinessException original = new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                "잘못된 사용자 ID입니다."
        );

        assertThatThrownBy(() -> adapter.getInterestConceptIdsFallback(
                UUID.randomUUID(),
                original
        )).isSameAs(original);
    }

    private void assertFeignClientError(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FEIGN_CLIENT_ERROR)
                );
    }

    private void assertUserLookupError(ThrowingCall call) {
        assertThatThrownBy(call::invoke).isInstanceOf(DailyQuizUserLookupException.class);
    }

    private FeignException feignError(int status) {
        return FeignException.errorStatus("getUser", Response.builder()
                .status(status)
                .reason("error")
                .request(request())
                .build());
    }

    private Request request() {
        return Request.create(Request.HttpMethod.GET,
                "http://user-service/internal/v1/users/" + UUID.randomUUID(),
                Map.of(), null, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void invoke();
    }
}
