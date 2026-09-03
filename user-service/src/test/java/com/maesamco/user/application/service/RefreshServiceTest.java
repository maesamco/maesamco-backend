package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionRotationResult;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.RefreshTokenVerifier;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.application.port.VerifiedRefreshToken;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RefreshService의 Refresh Token 검증,
 * Rotation 및 Reuse Detection 흐름을 검증하는 단위 테스트입니다.
 *
 * <p>Refresh Token 원문이 아닌 해시를 기준으로 Redis에서
 * 원자적 Rotation을 수행하며, 기존 인증 세션의 절대 만료 시각을
 * 유지하는지 검증합니다.</p>
 *
 * <p>이미 사용된 Refresh Token이 다시 사용되는 경우
 * AUTH_REFRESH_TOKEN_REUSED를 반환하고,
 * 세션이 존재하지 않는 경우 AUTH_TOKEN_REVOKED를 반환하는 등
 * 인증 실패 시나리오도 함께 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

    private static final String RAW_REFRESH_TOKEN =
            "old-refresh-token";

    private static final String OLD_REFRESH_TOKEN_HASH =
            "old-refresh-token-hash";

    private static final String NEW_REFRESH_TOKEN =
            "new-refresh-token";

    private static final String NEW_REFRESH_TOKEN_HASH =
            "new-refresh-token-hash";

    private static final String NEW_ACCESS_TOKEN =
            "new-access-token";

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID FAMILY_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID TOKEN_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final UUID OTHER_USER_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-04T00:00:00Z"
            );

    private static final Instant SESSION_CREATED_AT =
            NOW.minusSeconds(60);

    private static final Instant SESSION_EXPIRES_AT =
            NOW.plusSeconds(
                    60L * 60 * 24 * 7
            );

    private static final Instant ACCESS_TOKEN_EXPIRES_AT =
            NOW.plusSeconds(900);

    @Mock
    private RefreshTokenVerifier refreshTokenVerifier;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private AuthSessionStore authSessionStore;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private Clock clock;

    @InjectMocks
    private RefreshService refreshService;

    @Test
    @DisplayName(
            "Refresh에 성공하면 기존 세션 만료 시각을 유지하면서 "
                    + "Refresh Token을 Rotation한다"
    )
    void refresh() {
        // given
        User user = createActiveUser();

        VerifiedRefreshToken verifiedToken =
                createVerifiedRefreshToken(
                        user.getId()
                );

        AuthSession authSession =
                createAuthSession(
                        user.getId()
                );

        IssuedTokens issuedTokens =
                createIssuedTokens();

        when(
                refreshTokenVerifier.verify(
                        RAW_REFRESH_TOKEN
                )
        ).thenReturn(verifiedToken);

        when(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).thenReturn(
                Optional.of(authSession)
        );

        when(
                userRepository.findById(
                        user.getId()
                )
        ).thenReturn(
                Optional.of(user)
        );

        when(
                tokenIssuer.issueRotatedTokens(
                        user.getId(),
                        UserRole.USER,
                        SESSION_ID,
                        SESSION_EXPIRES_AT
                )
        ).thenReturn(issuedTokens);

        when(
                refreshTokenHasher.hash(
                        RAW_REFRESH_TOKEN
                )
        ).thenReturn(
                OLD_REFRESH_TOKEN_HASH
        );

        when(
                refreshTokenHasher.hash(
                        NEW_REFRESH_TOKEN
                )
        ).thenReturn(
                NEW_REFRESH_TOKEN_HASH
        );

        when(
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        OLD_REFRESH_TOKEN_HASH,
                        NEW_REFRESH_TOKEN_HASH
                )
        ).thenReturn(
                AuthSessionRotationResult.ROTATED
        );

        when(clock.instant())
                .thenReturn(NOW);

        RefreshCommand command =
                new RefreshCommand(
                        RAW_REFRESH_TOKEN
                );

        // when
        RefreshResult result =
                refreshService.refresh(command);

        // then
        verify(refreshTokenVerifier)
                .verify(
                        RAW_REFRESH_TOKEN
                );

        verify(authSessionStore)
                .findBySessionId(
                        SESSION_ID
                );

        verify(userRepository)
                .findById(
                        user.getId()
                );

        verify(tokenIssuer)
                .issueRotatedTokens(
                        user.getId(),
                        UserRole.USER,
                        SESSION_ID,
                        SESSION_EXPIRES_AT
                );

        verify(refreshTokenHasher)
                .hash(
                        RAW_REFRESH_TOKEN
                );

        verify(refreshTokenHasher)
                .hash(
                        NEW_REFRESH_TOKEN
                );

        verify(authSessionStore)
                .rotateRefreshToken(
                        SESSION_ID,
                        OLD_REFRESH_TOKEN_HASH,
                        NEW_REFRESH_TOKEN_HASH
                );

        assertThat(result.accessToken())
                .isEqualTo(
                        NEW_ACCESS_TOKEN
                );

        assertThat(result.accessTokenExpiresIn())
                .isEqualTo(900);

        assertThat(result.issuedTokens())
                .isSameAs(issuedTokens);

        assertThat(
                result.issuedTokens()
                        .refreshTokenExpiresAt()
        ).isEqualTo(
                SESSION_EXPIRES_AT
        );
    }

    @Test
    @DisplayName(
            "Refresh Token Cookie가 없으면 "
                    + "AUTH_UNAUTHORIZED를 반환한다"
    )
    void refresh_missingRefreshToken() {
        // given
        RefreshCommand command =
                new RefreshCommand(null);

        // when & then
        assertThatThrownBy(
                () -> refreshService.refresh(command)
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
                        ErrorCode.AUTH_UNAUTHORIZED
                );

        verify(
                refreshTokenVerifier,
                never()
        ).verify(any());
    }

    @Test
    @DisplayName(
            "Refresh Token Cookie가 공백이면 "
                    + "AUTH_UNAUTHORIZED를 반환한다"
    )
    void refresh_blankRefreshToken() {
        // given
        RefreshCommand command =
                new RefreshCommand("   ");

        // when & then
        assertThatThrownBy(
                () -> refreshService.refresh(command)
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
                        ErrorCode.AUTH_UNAUTHORIZED
                );

        verify(
                refreshTokenVerifier,
                never()
        ).verify(any());
    }

    @Test
    @DisplayName(
            "Refresh JWT의 인증 세션이 존재하지 않으면 "
                    + "AUTH_TOKEN_REVOKED를 반환한다"
    )
    void refresh_sessionNotFound() {
        // given
        User user = createActiveUser();

        VerifiedRefreshToken verifiedToken =
                createVerifiedRefreshToken(
                        user.getId()
                );

        when(
                refreshTokenVerifier.verify(
                        RAW_REFRESH_TOKEN
                )
        ).thenReturn(verifiedToken);

        when(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).thenReturn(
                Optional.empty()
        );

        RefreshCommand command =
                new RefreshCommand(
                        RAW_REFRESH_TOKEN
                );

        // when & then
        assertThatThrownBy(
                () -> refreshService.refresh(command)
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
                        ErrorCode.AUTH_TOKEN_REVOKED
                );

        verify(
                userRepository,
                never()
        ).findById(any());

        verify(
                tokenIssuer,
                never()
        ).issueRotatedTokens(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName(
            "Refresh JWT의 사용자와 인증 세션 소유자가 다르면 "
                    + "AUTH_INVALID_TOKEN을 반환한다"
    )
    void refresh_sessionOwnerMismatch() {
        // given
        User user = createActiveUser();

        VerifiedRefreshToken verifiedToken =
                createVerifiedRefreshToken(
                        user.getId()
                );

        AuthSession otherUserSession =
                createAuthSession(
                        OTHER_USER_ID
                );

        when(
                refreshTokenVerifier.verify(
                        RAW_REFRESH_TOKEN
                )
        ).thenReturn(verifiedToken);

        when(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).thenReturn(
                Optional.of(
                        otherUserSession
                )
        );

        RefreshCommand command =
                new RefreshCommand(
                        RAW_REFRESH_TOKEN
                );

        // when & then
        assertThatThrownBy(
                () -> refreshService.refresh(command)
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

        verify(
                userRepository,
                never()
        ).findById(any());

        verify(
                tokenIssuer,
                never()
        ).issueRotatedTokens(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName(
            "인증 세션 사용자가 존재하지 않으면 "
                    + "AUTH_TOKEN_REVOKED를 반환한다"
    )
    void refresh_userNotFound() {
        // given
        User user = createActiveUser();

        VerifiedRefreshToken verifiedToken =
                createVerifiedRefreshToken(
                        user.getId()
                );

        AuthSession authSession =
                createAuthSession(
                        user.getId()
                );

        when(
                refreshTokenVerifier.verify(
                        RAW_REFRESH_TOKEN
                )
        ).thenReturn(verifiedToken);

        when(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).thenReturn(
                Optional.of(authSession)
        );

        when(
                userRepository.findById(
                        user.getId()
                )
        ).thenReturn(
                Optional.empty()
        );

        RefreshCommand command =
                new RefreshCommand(
                        RAW_REFRESH_TOKEN
                );

        // when & then
        assertThatThrownBy(
                () -> refreshService.refresh(command)
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
                        ErrorCode.AUTH_TOKEN_REVOKED
                );

        verify(
                tokenIssuer,
                never()
        ).issueRotatedTokens(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName(
            "SUSPENDED 사용자는 Refresh를 거부한다"
    )
    void refresh_suspendedUser() {
        // given
        User user = createActiveUser();
        user.suspend();

        VerifiedRefreshToken verifiedToken =
                createVerifiedRefreshToken(
                        user.getId()
                );

        AuthSession authSession =
                createAuthSession(
                        user.getId()
                );

        when(
                refreshTokenVerifier.verify(
                        RAW_REFRESH_TOKEN
                )
        ).thenReturn(verifiedToken);

        when(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).thenReturn(
                Optional.of(authSession)
        );

        when(
                userRepository.findById(
                        user.getId()
                )
        ).thenReturn(
                Optional.of(user)
        );

        RefreshCommand command =
                new RefreshCommand(
                        RAW_REFRESH_TOKEN
                );

        // when & then
        assertThatThrownBy(
                () -> refreshService.refresh(command)
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
                        ErrorCode.USER_NOT_ACTIVE
                );

        verify(
                tokenIssuer,
                never()
        ).issueRotatedTokens(
                any(),
                any(),
                any(),
                any()
        );

        verify(
                authSessionStore,
                never()
        ).rotateRefreshToken(
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName(
            "Rotation 시점에 인증 세션이 사라졌으면 "
                    + "AUTH_TOKEN_REVOKED를 반환한다"
    )
    void refresh_rotationSessionNotFound() {
        // given
        User user = createActiveUser();

        prepareRotation(
                user,
                AuthSessionRotationResult
                        .SESSION_NOT_FOUND
        );

        RefreshCommand command =
                new RefreshCommand(
                        RAW_REFRESH_TOKEN
                );

        // when & then
        assertThatThrownBy(
                () -> refreshService.refresh(command)
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
                        ErrorCode.AUTH_TOKEN_REVOKED
                );

        verify(authSessionStore)
                .rotateRefreshToken(
                        SESSION_ID,
                        OLD_REFRESH_TOKEN_HASH,
                        NEW_REFRESH_TOKEN_HASH
                );
    }

    @Test
    @DisplayName(
            "이미 사용된 Refresh Token이면 "
                    + "AUTH_REFRESH_TOKEN_REUSED를 반환한다"
    )
    void refresh_reusedToken() {
        // given
        User user = createActiveUser();

        prepareRotation(
                user,
                AuthSessionRotationResult
                        .TOKEN_REUSED
        );

        RefreshCommand command =
                new RefreshCommand(
                        RAW_REFRESH_TOKEN
                );

        // when & then
        assertThatThrownBy(
                () -> refreshService.refresh(command)
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
                        ErrorCode.AUTH_REFRESH_TOKEN_REUSED
                );

        verify(authSessionStore)
                .rotateRefreshToken(
                        SESSION_ID,
                        OLD_REFRESH_TOKEN_HASH,
                        NEW_REFRESH_TOKEN_HASH
                );
    }

    /**
     * Rotation 결과 검증 테스트에서 공통으로 사용하는
     * 정상 Refresh 처리 과정을 준비합니다.
     *
     * @param user Refresh 대상 사용자
     * @param rotationResult Redis Rotation 결과
     */
    private void prepareRotation(
            User user,
            AuthSessionRotationResult rotationResult
    ) {
        VerifiedRefreshToken verifiedToken =
                createVerifiedRefreshToken(
                        user.getId()
                );

        AuthSession authSession =
                createAuthSession(
                        user.getId()
                );

        IssuedTokens issuedTokens =
                createIssuedTokens();

        when(
                refreshTokenVerifier.verify(
                        RAW_REFRESH_TOKEN
                )
        ).thenReturn(verifiedToken);

        when(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).thenReturn(
                Optional.of(authSession)
        );

        when(
                userRepository.findById(
                        user.getId()
                )
        ).thenReturn(
                Optional.of(user)
        );

        when(
                tokenIssuer.issueRotatedTokens(
                        user.getId(),
                        UserRole.USER,
                        SESSION_ID,
                        SESSION_EXPIRES_AT
                )
        ).thenReturn(issuedTokens);

        when(
                refreshTokenHasher.hash(
                        RAW_REFRESH_TOKEN
                )
        ).thenReturn(
                OLD_REFRESH_TOKEN_HASH
        );

        when(
                refreshTokenHasher.hash(
                        NEW_REFRESH_TOKEN
                )
        ).thenReturn(
                NEW_REFRESH_TOKEN_HASH
        );

        when(
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        OLD_REFRESH_TOKEN_HASH,
                        NEW_REFRESH_TOKEN_HASH
                )
        ).thenReturn(rotationResult);
    }

    /**
     * 테스트에서 사용할 정상 ACTIVE 사용자를 생성합니다.
     *
     * @return ACTIVE 사용자
     */
    private User createActiveUser() {
        return User.create(
                "encrypted-email",
                "a".repeat(64),
                "argon2-password-hash",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );
    }

    /**
     * 테스트 사용자의 정보가 포함된 검증 완료 Refresh JWT 정보를 생성합니다.
     *
     * @param userId Refresh Token 사용자 식별자
     * @return 검증 완료 Refresh Token 정보
     */
    private VerifiedRefreshToken createVerifiedRefreshToken(
            UUID userId
    ) {
        return new VerifiedRefreshToken(
                userId,
                SESSION_ID,
                TOKEN_ID.toString(),
                SESSION_EXPIRES_AT
        );
    }

    /**
     * Redis에 존재하는 테스트용 인증 세션을 생성합니다.
     *
     * @param userId 인증 세션 소유자
     * @return 인증 세션
     */
    private AuthSession createAuthSession(
            UUID userId
    ) {
        return new AuthSession(
                SESSION_ID,
                FAMILY_ID,
                userId,
                OLD_REFRESH_TOKEN_HASH,
                SESSION_CREATED_AT,
                SESSION_EXPIRES_AT
        );
    }

    /**
     * Refresh Rotation 이후 발급될 새로운 Access/Refresh Token을 생성합니다.
     *
     * @return 새로 발급된 토큰 정보
     */
    private IssuedTokens createIssuedTokens() {
        return new IssuedTokens(
                NEW_ACCESS_TOKEN,
                ACCESS_TOKEN_EXPIRES_AT,
                NEW_REFRESH_TOKEN,
                SESSION_EXPIRES_AT
        );
    }
}
