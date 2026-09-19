package com.maesamco.content.infrastructure.dailyquiz.adapter;

import com.maesamco.content.application.dailyquiz.result.DailyQuizTargetUserPage;
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
class DailyQuizTargetUserFeignAdapterTest {

    @Mock
    private UserServiceFeignClient feignClient;

    private DailyQuizTargetUserFeignAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DailyQuizTargetUserFeignAdapter(feignClient);
    }

    @Test
    @DisplayName("User Service의 대상 사용자 페이지 응답을 애플리케이션 모델로 변환한다")
    void getTargetUsers_mapsSuccessfulResponse() {
        UUID firstUserId = UUID.randomUUID();
        UUID lastUserId = UUID.randomUUID();
        int size = 100;

        when(feignClient.getQuizTargets(null, size))
                .thenReturn(SuccessResponse.success(
                        new UserQuizTargetPageResponse(
                                List.of(firstUserId, lastUserId),
                                lastUserId,
                                true
                        )
                ));

        DailyQuizTargetUserPage result = adapter.getTargetUsers(null, size);

        assertThat(result.userIds()).containsExactly(firstUserId, lastUserId);
        assertThat(result.nextCursor()).isEqualTo(lastUserId);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("다음 페이지가 없는 응답은 null cursor와 함께 변환한다")
    void getTargetUsers_mapsLastPage() {
        UUID cursor = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        int size = 50;

        when(feignClient.getQuizTargets(cursor, size))
                .thenReturn(SuccessResponse.success(
                        new UserQuizTargetPageResponse(
                                List.of(userId),
                                null,
                                false
                        )
                ));

        DailyQuizTargetUserPage result = adapter.getTargetUsers(cursor, size);

        assertThat(result.userIds()).containsExactly(userId);
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("공통 응답이 null이면 서비스 간 통신 오류로 처리한다")
    void getTargetUsers_rejectsNullResponse() {
        when(feignClient.getQuizTargets(null, 100)).thenReturn(null);

        assertFeignClientError(() -> adapter.getTargetUsers(null, 100));
    }

    @Test
    @DisplayName("실패 공통 응답이면 서비스 간 통신 오류로 처리한다")
    void getTargetUsers_rejectsUnsuccessfulResponse() {
        when(feignClient.getQuizTargets(null, 100))
                .thenReturn(new SuccessResponse<>(false, null));

        assertFeignClientError(() -> adapter.getTargetUsers(null, 100));
    }

    @Test
    @DisplayName("응답 data가 없으면 서비스 간 통신 오류로 처리한다")
    void getTargetUsers_rejectsMissingData() {
        when(feignClient.getQuizTargets(null, 100))
                .thenReturn(new SuccessResponse<>(true, null));

        assertFeignClientError(() -> adapter.getTargetUsers(null, 100));
    }

    @Test
    @DisplayName("hasNext가 없는 응답은 계약 위반으로 처리한다")
    void getTargetUsers_rejectsMissingHasNext() {
        when(feignClient.getQuizTargets(null, 100))
                .thenReturn(SuccessResponse.success(
                        new UserQuizTargetPageResponse(
                                List.of(),
                                null,
                                null
                        )
                ));

        assertFeignClientError(() -> adapter.getTargetUsers(null, 100));
    }

    @Test
    @DisplayName("사용자 ID 목록에 null이 있으면 애플리케이션 모델이 계약 위반으로 거부한다")
    void getTargetUsers_rejectsNullUserId() {
        when(feignClient.getQuizTargets(null, 100))
                .thenReturn(SuccessResponse.success(
                        new UserQuizTargetPageResponse(
                                java.util.Arrays.asList(UUID.randomUUID(), null),
                                null,
                                false
                        )
                ));

        assertThatThrownBy(() -> adapter.getTargetUsers(null, 100))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                );
    }

    @Test
    @DisplayName("통신 장애 fallback은 서비스 간 통신 오류로 변환한다")
    void getTargetUsersFallback_wrapsCommunicationFailure() {
        assertFeignClientError(() -> adapter.getTargetUsersFallback(
                null,
                100,
                new IllegalStateException("User Service 연결 실패")
        ));
    }

    @Test
    @DisplayName("fallback에 전달된 비즈니스 예외는 원래 오류를 유지한다")
    void getTargetUsersFallback_preservesBusinessException() {
        BusinessException original = new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                "잘못된 cursor입니다."
        );

        assertThatThrownBy(() -> adapter.getTargetUsersFallback(null, 100, original))
                .isSameAs(original);
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
