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
 * 사용자 회원가입과 자동 로그인용 인증 세션 생성을 처리합니다.
 *
 * <p>이메일 보호, 비밀번호 해시, 사용자와 초기 게이미피케이션 상태 저장,
 * JWT 발급 및 Redis 인증 세션 저장을 하나의 회원가입 흐름으로 조율합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignUpService {

    private final EmailNormalizer emailNormalizer;
    private final EmailCipher emailCipher;
    private final EmailLookupHasher emailLookupHasher;
    private final PasswordHasher passwordHasher;
    private final SignUpPersistenceService signUpPersistenceService;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthSessionStore authSessionStore;
    private final Clock clock;

    /**
     * 신규 사용자를 생성하고 자동 로그인용 인증 세션을 발급합니다.
     *
     * <p>사용자와 초기 게이미피케이션 상태 저장은
     * 별도의 DB 트랜잭션에서 처리하며,
     * JWT 발급과 Redis 인증 세션 저장은 해당 트랜잭션에 포함하지 않습니다.</p>
     *
     * @param command 회원가입 입력값
     * @return 생성된 사용자 정보와 발급된 인증 토큰 정보
     */
    public SignUpResult signUp(SignUpCommand command) {
        Objects.requireNonNull(command, "회원가입 명령은 필수입니다.");

        String normalizedEmail =
                emailNormalizer.normalize(command.email());

        String emailLookupHash =
                emailLookupHasher.hash(normalizedEmail);

        String normalizedNickname =
                normalizeNickname(command.nickname());

        signUpPersistenceService.validateNotDuplicated(
                emailLookupHash,
                normalizedNickname
        );

        String encryptedEmail =
                emailCipher.encrypt(normalizedEmail);

        String passwordHash =
                passwordHasher.hash(command.password());

        User user = User.create(
                encryptedEmail,
                emailLookupHash,
                passwordHash,
                normalizedNickname,
                command.javaExperienceMonths(),
                command.learningLevel()
        );

        User savedUser =
                signUpPersistenceService.saveUser(user);

        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        IssuedTokens issuedTokens =
                tokenIssuer.issueTokens(
                        savedUser.getId(),
                        savedUser.getRole(),
                        sessionId
                );

        Instant now = clock.instant();

        AuthSession authSession = new AuthSession(
                sessionId,
                familyId,
                savedUser.getId(),
                refreshTokenHasher.hash(
                        issuedTokens.refreshToken()
                ),
                now,
                issuedTokens.refreshTokenExpiresAt()
        );

        try {
            authSessionStore.save(authSession);
        } catch (RuntimeException exception) {
            log.warn(
                    "회원가입 완료 후 Redis 인증 세션 저장에 실패했습니다. "
                            + "userId={}, sessionId={}",
                    savedUser.getId(),
                    sessionId,
                    exception
            );

            throw new BusinessException(
                    ErrorCode.SIGNUP_AUTO_LOGIN_FAILED
            );
        }

        log.info(
                "회원가입이 완료되었습니다. userId={}",
                savedUser.getId()
        );

        long accessTokenExpiresIn =
                TokenExpirationCalculator.remainingSeconds(
                        now,
                        issuedTokens.accessTokenExpiresAt()
                );

        return new SignUpResult(
                savedUser.getId(),
                savedUser.getNickname(),
                savedUser.getRole(),
                savedUser.getStatus(),
                savedUser.getJavaExperienceMonths(),
                savedUser.getLearningLevel(),
                issuedTokens.accessToken(),
                accessTokenExpiresIn,
                issuedTokens
        );
    }

    /**
     * 중복 검사와 저장에 사용할 닉네임의 앞뒤 공백을 제거합니다.
     *
     * <p>필수값과 길이 검증은 {@link User#create}에서 수행합니다.</p>
     */
    private String normalizeNickname(String nickname) {
        if (nickname == null) {
            return null;
        }

        return nickname.trim();
    }
}
