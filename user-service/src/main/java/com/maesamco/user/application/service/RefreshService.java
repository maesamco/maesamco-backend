package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionRotationResult;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.RefreshTokenVerifier;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.application.port.VerifiedRefreshToken;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.security.TokenExpirationCalculator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Refresh Token을 검증하고 새로운 Access Token 및 Refresh Token을
 * 발급하는 Rotation 흐름을 처리합니다.
 *
 * <p>현재 Refresh Token hash와 새로운 Refresh Token hash의 교체는
 * Redis의 원자적 Rotation 연산을 통해 수행합니다.</p>
 *
 * <p>Rotation 과정에서도 최초 인증 세션의 절대 만료 시각을 유지하여
 * Refresh 요청만으로 세션 수명이 계속 연장되지 않도록 합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshService {

    private final RefreshTokenVerifier refreshTokenVerifier;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthSessionStore authSessionStore;
    private final UserRepository userRepository;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    /**
     * Refresh Token을 검증하고 새로운 인증 토큰으로 Rotation합니다.
     *
     * <p>Refresh Token 원문은 저장하지 않으며,
     * Redis에는 SHA-256 해시만 저장합니다.</p>
     *
     * <p>새로운 Refresh Token을 발급한 뒤 Redis Lua Script가
     * 기존 Refresh Token hash와 현재 저장된 hash를 원자적으로 비교합니다.
     * 직전 Refresh Token이 grace window 안에 다시 요청되면 세션은 유지하지만
     * 해당 요청은 거부합니다. Grace window를 벗어난 재사용이나 직전 토큰과
     * 무관한 토큰이 사용되면 인증 세션을 폐기하고 재로그인을 요구합니다.</p>
     *
     * @param command Refresh Token 재발급 입력값
     * @return 새 Access Token과 Refresh Token 발급 정보
     */
    public RefreshResult refresh(RefreshCommand command) {
        Objects.requireNonNull(
                command,
                "Refresh 명령은 필수입니다."
        );

        String refreshToken =
                requireRefreshToken(command.refreshToken());

        VerifiedRefreshToken verifiedToken =
                refreshTokenVerifier.verify(refreshToken);

        AuthSession authSession =
                authSessionStore
                        .findBySessionId(
                                verifiedToken.sessionId()
                        )
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.AUTH_TOKEN_REVOKED
                                )
                        );

        validateSessionOwner(
                verifiedToken,
                authSession
        );

        User user =
                userRepository
                        .findById(
                                verifiedToken.userId()
                        )
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.AUTH_TOKEN_REVOKED
                                )
                        );

        validateActiveUser(user);

        IssuedTokens issuedTokens =
                tokenIssuer.issueRotatedTokens(
                        user.getId(),
                        user.getRole(),
                        authSession.sessionId(),
                        authSession.expiresAt()
                );

        String expectedRefreshTokenHash =
                refreshTokenHasher.hash(refreshToken);

        String newRefreshTokenHash =
                refreshTokenHasher.hash(
                        issuedTokens.refreshToken()
                );

        AuthSessionRotationResult rotationResult =
                authSessionStore.rotateRefreshToken(
                        authSession.sessionId(),
                        expectedRefreshTokenHash,
                        newRefreshTokenHash
                );

        validateRotationResult(
                rotationResult,
                user.getId(),
                authSession.sessionId()
        );

        Instant now = clock.instant();

        long accessTokenExpiresIn =
                TokenExpirationCalculator.remainingSeconds(
                        now,
                        issuedTokens.accessTokenExpiresAt()
                );

        return new RefreshResult(
                issuedTokens.accessToken(),
                accessTokenExpiresIn,
                issuedTokens
        );
    }

    /**
     * Refresh Token Cookie가 존재하는지 확인합니다.
     *
     * <p>Cookie 자체가 없거나 공백인 경우 잘못된 입력값이 아니라
     * 인증정보가 없는 상태이므로 AUTH_UNAUTHORIZED로 처리합니다.</p>
     *
     * @param refreshToken Refresh Token 원문
     * @return 검증된 Refresh Token
     */
    private String requireRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(
                    ErrorCode.AUTH_UNAUTHORIZED
            );
        }

        return refreshToken;
    }

    /**
     * Refresh JWT의 사용자와 Redis 인증 세션의 소유자가
     * 동일한지 확인합니다.
     *
     * <p>서명된 JWT의 sessionId가 다른 사용자의 인증 세션을 가리키는
     * 비정상적인 상황에서는 해당 세션을 삭제하지 않고
     * 유효하지 않은 토큰으로 처리합니다.</p>
     *
     * @param verifiedToken 검증된 Refresh JWT 정보
     * @param authSession Redis 인증 세션
     */
    private void validateSessionOwner(
            VerifiedRefreshToken verifiedToken,
            AuthSession authSession
    ) {
        if (!verifiedToken.userId().equals(
                authSession.userId()
        )) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }
    }

    /**
     * 사용자가 Refresh 가능한 ACTIVE 상태인지 확인합니다.
     *
     * @param user 인증 세션의 사용자
     */
    private void validateActiveUser(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.USER_NOT_ACTIVE
            );
        }
    }

    /**
     * Redis에서 수행한 원자적 Refresh Token Rotation 결과를
     * 인증 오류로 변환합니다.
     *
     * <p>grace window 안에서 직전 Refresh Token이 다시 요청되면
     * 세션을 유지하고 DEBUG 로그를 남깁니다. 실제 재사용으로 판단해
     * 세션을 폐기한 경우에만 보안 이벤트 추적을 위한 WARN 로그를 남깁니다.
     * Refresh Token 원문과 hash는 기록하지 않습니다.</p>
     *
     * @param rotationResult Redis Rotation 결과
     * @param userId 인증 세션 사용자 ID
     * @param sessionId 인증 세션 ID
     */
    private void validateRotationResult(
            AuthSessionRotationResult rotationResult,
            UUID userId,
            UUID sessionId
    ) {
        Objects.requireNonNull(
                rotationResult,
                "Refresh Token Rotation 결과는 필수입니다."
        );

        switch (rotationResult) {
            case ROTATED -> {
                return;
            }

            case SESSION_NOT_FOUND ->
                    throw new BusinessException(
                            ErrorCode.AUTH_TOKEN_REVOKED
                    );

            case PREVIOUS_TOKEN_WITHIN_GRACE -> {
                log.debug(
                        "grace window 안에서 직전 Refresh Token이 "
                                + "다시 요청되었습니다. "
                                + "userId={}, sessionId={}",
                        userId,
                        sessionId
                );

                throw new BusinessException(
                        ErrorCode.AUTH_REFRESH_TOKEN_REUSED
                );
            }

            case TOKEN_REUSED -> {
                log.warn(
                        "Refresh Token 재사용 감지. userId={}, sessionId={}",
                        userId,
                        sessionId
                );

                throw new BusinessException(
                        ErrorCode.AUTH_REFRESH_TOKEN_REUSED
                );
            }
        }
    }
}
