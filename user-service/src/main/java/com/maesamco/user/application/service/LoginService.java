package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 이메일과 비밀번호를 이용한 사용자 로그인과
 * 인증 세션 생성을 처리합니다.
 *
 * <p>사용자 조회와 비밀번호 검증, 계정 상태 확인,
 * Access Token 및 Refresh Token 발급,
 * Redis 인증 세션 저장을 하나의 로그인 흐름으로 조율합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final EmailNormalizer emailNormalizer;
    private final EmailLookupHasher emailLookupHasher;
    private final EmailCipher emailCipher;
    private final PasswordHasher passwordHasher;
    private final UserRepository userRepository;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthSessionStore authSessionStore;
    private final Clock clock;

    /**
     * 이메일과 비밀번호를 검증하고 새로운 로그인 인증 세션을 생성합니다.
     *
     * <p>존재하지 않는 사용자와 비밀번호 불일치는
     * 사용자 존재 여부가 외부에 노출되지 않도록
     * 동일한 {@link ErrorCode#INVALID_CREDENTIALS}로 처리합니다.</p>
     *
     * <p>로그인 과정에서는 PostgreSQL에 변경사항을 저장하지 않습니다.
     * Redis 인증 세션 저장에 실패한 경우 예외를 그대로 전파하여
     * 발급된 토큰이 클라이언트에게 전달되지 않도록 합니다.</p>
     *
     * @param command 로그인 입력값
     * @return 로그인 사용자 정보와 발급된 인증 토큰 정보
     */
    public LoginResult login(LoginCommand command) {
        Objects.requireNonNull(
                command,
                "로그인 명령은 필수입니다."
        );

        String normalizedEmail =
                emailNormalizer.normalize(command.email());

        String emailLookupHash =
                emailLookupHasher.hash(normalizedEmail);

        User user = userRepository
                .findByEmailLookupHash(emailLookupHash)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.INVALID_CREDENTIALS
                        )
                );

        validatePassword(
                command.password(),
                user.getPasswordHash()
        );

        validateActiveUser(user);

        String decryptedEmail =
                emailCipher.decrypt(
                        user.getEncryptedEmail()
                );

        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        IssuedTokens issuedTokens =
                tokenIssuer.issueTokens(
                        user.getId(),
                        user.getRole(),
                        sessionId
                );

        Instant now = clock.instant();

        String refreshTokenHash =
                refreshTokenHasher.hash(
                        issuedTokens.refreshToken()
                );

        AuthSession authSession =
                new AuthSession(
                        sessionId,
                        familyId,
                        user.getId(),
                        refreshTokenHash,
                        now,
                        issuedTokens.refreshTokenExpiresAt()
                );

        authSessionStore.save(authSession);

        long accessTokenExpiresIn =
                calculateExpiresInSeconds(
                        now,
                        issuedTokens.accessTokenExpiresAt()
                );

        LoginResult.UserInfo userInfo =
                new LoginResult.UserInfo(
                        user.getId(),
                        decryptedEmail,
                        user.getNickname(),
                        user.getRole(),
                        user.getStatus(),
                        user.getLearningLevel()
                );

        return new LoginResult(
                userInfo,
                issuedTokens.accessToken(),
                accessTokenExpiresIn,
                issuedTokens
        );
    }

    /**
     * 입력 비밀번호와 저장된 비밀번호 해시가 일치하는지 확인합니다.
     *
     * <p>비밀번호 불일치는 사용자 조회 실패와 동일한
     * INVALID_CREDENTIALS 오류로 처리합니다.</p>
     *
     * @param rawPassword 로그인 요청의 비밀번호 원문
     * @param passwordHash 저장된 비밀번호 해시
     */
    private void validatePassword(
            String rawPassword,
            String passwordHash
    ) {
        if (!passwordHasher.matches(
                rawPassword,
                passwordHash
        )) {
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }
    }

    /**
     * 사용자가 로그인 가능한 ACTIVE 상태인지 확인합니다.
     *
     * <p>비밀번호 인증에 성공한 뒤 상태를 확인하여
     * 사용자 존재 여부와 상태가 불필요하게 노출되지 않도록 합니다.</p>
     *
     * @param user 인증된 사용자
     */
    private void validateActiveUser(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.USER_NOT_ACTIVE
            );
        }
    }

    /**
     * Access Token 만료 시각을 현재 시각 기준
     * 남은 초 단위로 변환합니다.
     *
     * <p>밀리초 단위의 일부 시간이 남아 있는 경우
     * 회원가입과 동일하게 초 단위로 올림 처리합니다.</p>
     *
     * @param now 현재 시각
     * @param expiresAt Access Token 만료 시각
     * @return Access Token 만료까지 남은 시간(초)
     */
    private long calculateExpiresInSeconds(
            Instant now,
            Instant expiresAt
    ) {
        Objects.requireNonNull(
                expiresAt,
                "Access Token 만료 시각은 필수입니다."
        );

        long remainingMillis =
                Duration.between(
                        now,
                        expiresAt
                ).toMillis();

        if (remainingMillis <= 0) {
            return 0;
        }

        return (remainingMillis + 999L) / 1000L;
    }
}
