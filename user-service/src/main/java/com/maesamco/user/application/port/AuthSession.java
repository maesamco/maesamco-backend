package com.maesamco.user.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Redis에 저장하는 사용자 인증 세션입니다.
 *
 * <p>Refresh Token 원문은 저장하지 않고 단방향 해시만 저장합니다.
 * familyId는 Refresh Token 회전 과정에서 동일한 토큰 계열을
 * 식별하기 위해 사용합니다.</p>
 *
 * @param sessionId 인증 세션 식별자
 * @param familyId Refresh Token 계열 식별자
 * @param userId 세션 소유자 식별자
 * @param refreshTokenHash 현재 Refresh Token 단방향 해시
 * @param createdAt 세션 생성 시각
 * @param expiresAt 세션 만료 시각
 * @param previousRefreshTokenHash 직전 Refresh Token 단방향 해시
 * @param refreshTokenRotatedAtEpochMillis 마지막 Rotation 시각
 */
public record AuthSession(
        UUID sessionId,
        UUID familyId,
        UUID userId,
        String refreshTokenHash,
        Instant createdAt,
        Instant expiresAt,
        String previousRefreshTokenHash,
        Long refreshTokenRotatedAtEpochMillis
) {

    /**
     * 최초 생성되는 인증 세션을 만듭니다.
     */
    public AuthSession(
            UUID sessionId,
            UUID familyId,
            UUID userId,
            String refreshTokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        this(
                sessionId,
                familyId,
                userId,
                refreshTokenHash,
                createdAt,
                expiresAt,
                null,
                null
        );
    }

    /**
     * 인증 세션의 필수값과 만료 시각 및 Rotation 이력을 검증합니다.
     */
    public AuthSession {
        Objects.requireNonNull(
                sessionId,
                "세션 식별자는 필수입니다."
        );
        Objects.requireNonNull(
                familyId,
                "토큰 계열 식별자는 필수입니다."
        );
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );
        Objects.requireNonNull(
                refreshTokenHash,
                "Refresh Token 해시는 필수입니다."
        );
        Objects.requireNonNull(
                createdAt,
                "세션 생성 시각은 필수입니다."
        );
        Objects.requireNonNull(
                expiresAt,
                "세션 만료 시각은 필수입니다."
        );

        if (refreshTokenHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Refresh Token 해시는 비어 있을 수 없습니다."
            );
        }

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "세션 만료 시각은 생성 시각보다 이후여야 합니다."
            );
        }

        boolean hasPreviousHash =
                previousRefreshTokenHash != null;

        boolean hasRotatedAt =
                refreshTokenRotatedAtEpochMillis != null;

        if (hasPreviousHash != hasRotatedAt) {
            throw new IllegalArgumentException(
                    "직전 Refresh Token 해시와 Rotation 시각은 함께 존재해야 합니다."
            );
        }

        if (hasPreviousHash
                && previousRefreshTokenHash.isBlank()) {
            throw new IllegalArgumentException(
                    "직전 Refresh Token 해시는 비어 있을 수 없습니다."
            );
        }

        if (hasRotatedAt
                && refreshTokenRotatedAtEpochMillis < 0) {
            throw new IllegalArgumentException(
                    "Refresh Token Rotation 시각은 음수일 수 없습니다."
            );
        }
    }
}
