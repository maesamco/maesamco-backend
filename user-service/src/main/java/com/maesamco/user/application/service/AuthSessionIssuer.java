package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.security.TokenExpirationCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 로그인·회원가입이 끝난 사용자에게 새 인증 세션을 발급합니다(#339).
 *
 * <p>일반 로그인, 소셜 로그인, 일반 회원가입, 소셜 회원가입이
 * 모두 이 컴포넌트로 세션을 발급합니다. 세션 정책(식별자 생성, 토큰 발급,
 * Refresh Token 해시 저장, 만료 시간 계산)은 이곳에서만 바꿉니다.</p>
 *
 * <p>Refresh Token 회전({@link RefreshService})은 기존 세션 계열을 이어가는
 * 별도 흐름이므로 사용하지 않습니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionIssuer {

    private final TokenIssuer tokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthSessionStore authSessionStore;
    private final Clock clock;

    /**
     * 사용자에게 새 세션 계열(familyId)의 인증 세션을 발급하고 Redis에 저장합니다.
     *
     * <p>처리 순서</p>
     * <ol>
     *     <li>sessionId·familyId 생성, 세션 시작 시각 기록</li>
     *     <li>Access Token·Refresh Token 발급</li>
     *     <li>Refresh Token 해시만 담은 AuthSession 저장</li>
     *     <li>저장 이후 시각 기준으로 Access Token 남은 시간 계산</li>
     * </ol>
     *
     * <p>세션 저장에 실패하면 토큰을 반환하지 않습니다.
     * 실패 처리 방식은 {@link AuthSessionPurpose}를 따릅니다.</p>
     *
     * @param user 세션을 발급할 사용자 (저장된 사용자여야 합니다)
     * @param purpose 세션을 발급하는 흐름
     * @return 발급된 세션 정보
     */
    public IssuedAuthSession issue(
            User user,
            AuthSessionPurpose purpose
    ) {
        Objects.requireNonNull(
                user,
                "사용자는 필수입니다."
        );

        Objects.requireNonNull(
                purpose,
                "인증 세션 발급 흐름은 필수입니다."
        );

        UUID sessionId =
                UUID.randomUUID();

        UUID familyId =
                UUID.randomUUID();

        Instant sessionStartedAt =
                clock.instant();

        IssuedTokens issuedTokens =
                tokenIssuer.issueTokens(
                        user.getId(),
                        user.getRole(),
                        sessionId
                );

        AuthSession authSession =
                new AuthSession(
                        sessionId,
                        familyId,
                        user.getId(),
                        refreshTokenHasher.hash(
                                issuedTokens.refreshToken()
                        ),
                        sessionStartedAt,
                        issuedTokens.refreshTokenExpiresAt()
                );

        save(
                authSession,
                purpose
        );

        long accessTokenExpiresIn =
                TokenExpirationCalculator.remainingSeconds(
                        clock.instant(),
                        issuedTokens.accessTokenExpiresAt()
                );

        return new IssuedAuthSession(
                sessionId,
                issuedTokens,
                accessTokenExpiresIn
        );
    }

    private void save(
            AuthSession authSession,
            AuthSessionPurpose purpose
    ) {
        if (!purpose.isSignupAutoLogin()) {
            authSessionStore.save(
                    authSession
            );
            return;
        }

        try {
            authSessionStore.save(
                    authSession
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "{} 완료 후 Redis 인증 세션 저장에 실패했습니다. "
                            + "userId={}, sessionId={}",
                    purpose.description(),
                    authSession.userId(),
                    authSession.sessionId(),
                    exception
            );

            throw new BusinessException(
                    ErrorCode.SIGNUP_AUTO_LOGIN_FAILED
            );
        }
    }
}
