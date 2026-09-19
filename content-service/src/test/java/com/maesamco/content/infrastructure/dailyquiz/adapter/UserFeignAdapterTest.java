package com.maesamco.content.infrastructure.dailyquiz.adapter;

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
    @DisplayName("공통 응답이 null이면 콜드스타트 후보 없이 처리한다")
    void getInterestConceptIds_returnsEmptyListForNullResponse() {
        UUID userId = UUID.randomUUID();
        when(feignClient.getUser(userId)).thenReturn(null);

        assertThat(adapter.getInterestConceptIds(userId)).isEmpty();
    }

    @Test
    @DisplayName("실패 공통 응답이면 콜드스타트 후보 없이 처리한다")
    void getInterestConceptIds_returnsEmptyListForUnsuccessfulResponse() {
        UUID userId = UUID.randomUUID();
        when(feignClient.getUser(userId))
                .thenReturn(new SuccessResponse<>(
                        false,
                        new UserInterestConceptResponse(List.of(UUID.randomUUID()))
                ));

        assertThat(adapter.getInterestConceptIds(userId)).isEmpty();
    }

    @Test
    @DisplayName("응답 data가 없으면 콜드스타트 후보 없이 처리한다")
    void getInterestConceptIds_returnsEmptyListForMissingData() {
        UUID userId = UUID.randomUUID();
        when(feignClient.getUser(userId))
                .thenReturn(new SuccessResponse<>(true, null));

        assertThat(adapter.getInterestConceptIds(userId)).isEmpty();
    }

    @Test
    @DisplayName("통신 장애 fallback은 특정 사용자의 관심 개념만 빈 목록으로 처리한다")
    void getInterestConceptIdsFallback_returnsEmptyList() {
        List<UUID> result = adapter.getInterestConceptIdsFallback(
                UUID.randomUUID(),
                new IllegalStateException("User Service 연결 실패")
        );

        assertThat(result).isEmpty();
    }
}
