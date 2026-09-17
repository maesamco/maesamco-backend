package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 인증된 사용자의 모든 로그인 세션을 종료합니다.
 *
 * <p>모든 Refresh Token 기반 인증 세션 제거와
 * 기존 Access Token 사용자 단위 무효화는
 * AuthSessionLogoutAllStore에 위임합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class LogoutAllService {

    private final AuthSessionLogoutAllStore authSessionLogoutAllStore;
    private final Clock clock;

    /**
     * 인증된 사용자의 모든 기기 로그인을 종료합니다.
     *
     * @param command 인증된 사용자 정보
     */
    public void logoutAll(
            LogoutAllCommand command
    ) {
        Objects.requireNonNull(
                command,
                "전체 로그아웃 명령은 필수입니다."
        );

        Instant invalidatedAt =
                clock.instant();

        authSessionLogoutAllStore.logoutAll(
                command.userId(),
                invalidatedAt
        );
    }
}
