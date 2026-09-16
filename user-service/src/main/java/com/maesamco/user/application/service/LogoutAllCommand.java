package com.maesamco.user.application.service;

import java.util.Objects;
import java.util.UUID;

/**
 * 전체 기기 로그아웃에 필요한 입력값입니다.
 *
 * @param userId 인증된 사용자 식별자
 */
public record LogoutAllCommand(
        UUID userId
) {

    /**
     * 전체 로그아웃 입력값의 필수값을 검증합니다.
     */
    public LogoutAllCommand {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );
    }
}
