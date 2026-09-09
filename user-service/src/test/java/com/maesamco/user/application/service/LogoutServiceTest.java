package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutResult;
import com.maesamco.user.application.port.AuthSessionLogoutStore;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * LogoutService의 현재 인증 세션 로그아웃 정책을 검증합니다.
 *
 * <p>현재 세션 삭제와 세션 블랙리스트 등록은
 * AuthSessionLogoutStore의 원자 연산에 위임합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final Instant ACCESS_TOKEN_EXPIRES_AT =
            Instant.parse(
                    "2026-09-07T12:00:00Z"
            );

    @Mock
    private AuthSessionLogoutStore authSessionLogoutStore;

    @InjectMocks
    private LogoutService logoutService;

    @Test
    @DisplayName(
            "현재 인증 세션 로그아웃에 성공하면 "
                    + "세션 삭제와 블랙리스트 등록을 요청한다"
    )
    void logout() {
        // given
        LogoutCommand command =
                createLogoutCommand();

        when(
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                )
        ).thenReturn(
                AuthSessionLogoutResult.LOGGED_OUT
        );

        // when & then
        assertThatCode(
                () -> logoutService.logout(command)
        ).doesNotThrowAnyException();

        verify(authSessionLogoutStore)
                .logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );
    }

    @Test
    @DisplayName(
            "인증 세션이 이미 삭제됐어도 "
                    + "반복 로그아웃 요청을 성공으로 처리한다"
    )
    void logout_sessionNotFound() {
        // given
        LogoutCommand command =
                createLogoutCommand();

        when(
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                )
        ).thenReturn(
                AuthSessionLogoutResult.SESSION_NOT_FOUND
        );

        // when & then
        assertThatCode(
                () -> logoutService.logout(command)
        ).doesNotThrowAnyException();

        verify(authSessionLogoutStore)
                .logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );
    }

    @Test
    @DisplayName(
            "인증된 사용자와 세션 소유자가 다르면 "
                    + "AUTH_INVALID_TOKEN을 반환한다"
    )
    void logout_sessionOwnerMismatch() {
        // given
        LogoutCommand command =
                createLogoutCommand();

        when(
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                )
        ).thenReturn(
                AuthSessionLogoutResult
                        .SESSION_OWNER_MISMATCH
        );

        // when & then
        assertThatThrownBy(
                () -> logoutService.logout(command)
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.AUTH_INVALID_TOKEN
                );

        verify(authSessionLogoutStore)
                .logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );
    }

    @Test
    @DisplayName(
            "로그아웃 저장소가 처리 결과를 반환하지 않으면 실패한다"
    )
    void logout_nullResult() {
        // given
        LogoutCommand command =
                createLogoutCommand();

        when(
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                )
        ).thenReturn(null);

        // when & then
        assertThatThrownBy(
                () -> logoutService.logout(command)
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "인증 세션 로그아웃 결과는 필수입니다."
                );
    }

    @Test
    @DisplayName("로그아웃 명령이 없으면 저장소를 호출하지 않는다")
    void logout_nullCommand() {
        // when & then
        assertThatThrownBy(
                () -> logoutService.logout(null)
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "로그아웃 명령은 필수입니다."
                );

        verifyNoInteractions(
                authSessionLogoutStore
        );
    }

    /**
     * 테스트에서 사용할 정상 로그아웃 명령을 생성합니다.
     */
    private LogoutCommand createLogoutCommand() {
        return new LogoutCommand(
                USER_ID,
                SESSION_ID,
                ACCESS_TOKEN_EXPIRES_AT
        );
    }
}
