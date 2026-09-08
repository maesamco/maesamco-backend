package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutResult;
import com.maesamco.user.application.port.AuthSessionLogoutStore;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 현재 로그인 요청에 사용된 인증 세션을 종료합니다.
 *
 * <p>인증 세션 삭제와 세션 블랙리스트 등록은
 * AuthSessionLogoutStore의 원자 연산에 위임합니다.</p>
 *
 * <p>이미 인증 세션이 삭제된 경우에도 블랙리스트 등록이
 * 완료됐다면 반복 로그아웃 요청을 성공으로 처리합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final AuthSessionLogoutStore authSessionLogoutStore;

    /**
     * 현재 인증 세션을 종료합니다.
     *
     * @param command 인증된 사용자와 현재 Access Token의 세션 정보
     */
    public void logout(LogoutCommand command) {
        Objects.requireNonNull(
                command,
                "로그아웃 명령은 필수입니다."
        );

        AuthSessionLogoutResult result =
                authSessionLogoutStore.logout(
                        command.userId(),
                        command.sessionId(),
                        command.accessTokenExpiresAt()
                );

        validateLogoutResult(result);
    }

    /**
     * Redis 원자 로그아웃 결과를 서비스 정책에 따라 처리합니다.
     *
     * @param result Redis 로그아웃 처리 결과
     */
    private void validateLogoutResult(
            AuthSessionLogoutResult result
    ) {
        Objects.requireNonNull(
                result,
                "인증 세션 로그아웃 결과는 필수입니다."
        );

        switch (result) {
            case LOGGED_OUT, SESSION_NOT_FOUND -> {
                return;
            }

            case SESSION_OWNER_MISMATCH ->
                    throw new BusinessException(
                            ErrorCode.AUTH_INVALID_TOKEN
                    );
        }
    }
}
