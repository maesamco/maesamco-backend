package com.maesamco.content.infrastructure.dailyquiz.adapter;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.SuccessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
    @DisplayName("공통 응답이 null이면 서비스 간 통신 오류로 처리한다")
    void getInterestConceptIds_rejectsNullResponse() {
        UUID userId = UUID.randomUUID();
        when(feignClient.getUser(userId)).thenReturn(null);

        assertFeignClientError(() -> adapter.getInterestConceptIds(userId));
    }

    @Test
    @DisplayName("실패 공통 응답이면 서비스 간 통신 오류로 처리한다")
    void getInterestConceptIds_rejectsUnsuccessfulResponse() {
        UUID userId = UUID.randomUUID();
        when(feignClient.getUser(userId))
                .thenReturn(new SuccessResponse<>(
                        false,
                        new UserInterestConceptResponse(List.of(UUID.randomUUID()))
                ));

        assertFeignClientError(() -> adapter.getInterestConceptIds(userId));
    }

    @Test
    @DisplayName("응답 data가 없으면 서비스 간 통신 오류로 처리한다")
    void getInterestConceptIds_rejectsMissingData() {
        UUID userId = UUID.randomUUID();
        when(feignClient.getUser(userId))
                .thenReturn(new SuccessResponse<>(true, null));

        assertFeignClientError(() -> adapter.getInterestConceptIds(userId));
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

    @FunctionalInterface
    private interface ThrowingCall {
        void invoke();
    }
}
