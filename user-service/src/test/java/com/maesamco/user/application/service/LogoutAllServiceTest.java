package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * LogoutAllService의 전체 기기 로그아웃 정책을 검증합니다.
 *
 * <p>인증된 사용자의 모든 Refresh Token 기반 인증 세션 제거와
 * 기존 Access Token의 사용자 단위 무효화 처리는
 * AuthSessionLogoutAllStore에 위임합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class LogoutAllServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final Instant INVALIDATED_AT =
            Instant.parse(
                    "2026-09-14T11:00:00Z"
            );

    @Mock
    private AuthSessionLogoutAllStore authSessionLogoutAllStore;

    @Mock
    private Clock clock;

    @InjectMocks
    private LogoutAllService logoutAllService;

    @Test
    @DisplayName(
            "전체 기기 로그아웃 시 사용자 식별자와 "
                    + "현재 시각으로 모든 인증 세션 무효화를 요청한다"
    )
    void logoutAll() {
        // given
        LogoutAllCommand command =
                new LogoutAllCommand(
                        USER_ID
                );

        when(clock.instant())
                .thenReturn(
                        INVALIDATED_AT
                );

        // when & then
        assertThatCode(
                () -> logoutAllService.logoutAll(command)
        ).doesNotThrowAnyException();

        verify(authSessionLogoutAllStore)
                .logoutAll(
                        USER_ID,
                        INVALIDATED_AT
                );
    }

    @Test
    @DisplayName(
            "전체 로그아웃 명령이 없으면 "
                    + "현재 시각을 조회하거나 저장소를 호출하지 않는다"
    )
    void logoutAll_nullCommand() {
        // when & then
        assertThatThrownBy(
                () -> logoutAllService.logoutAll(null)
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "전체 로그아웃 명령은 필수입니다."
                );

        verifyNoInteractions(
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName(
            "전체 로그아웃 명령에 사용자 식별자가 없으면 실패한다"
    )
    void logoutAll_nullUserId() {
        // when & then
        assertThatThrownBy(
                () -> new LogoutAllCommand(null)
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "사용자 식별자는 필수입니다."
                );

        verifyNoInteractions(
                authSessionLogoutAllStore,
                clock
        );
    }
}
