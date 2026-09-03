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
import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 사용자 회원가입과 자동 로그인용 인증 세션 생성을 처리합니다.
 *
 * <p>이메일 보호, 비밀번호 해시, 사용자와 초기 게이미피케이션 상태 저장,
 * JWT 발급 및 Redis 인증 세션 저장을 하나의 회원가입 흐름으로 조율합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class SignUpService {

    private final EmailNormalizer emailNormalizer;
    private final EmailCipher emailCipher;
    private final EmailLookupHasher emailLookupHasher;
    private final PasswordHasher passwordHasher;
    private final UserRepository userRepository;
    private final UserGamificationStateRepository
            gamificationStateRepository;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthSessionStore authSessionStore;
    private final Clock clock;

    /**
     * 신규 사용자를 생성하고 자동 로그인용 인증 세션을 발급합니다.
     *
     * <p>PostgreSQL 트랜잭션과 Redis 저장은 하나의 ACID 트랜잭션으로 묶이지 않으므로,
     * Redis 인증 세션을 저장한 뒤 DB 트랜잭션이 롤백되는 경우
     * 해당 Redis 세션을 삭제하는 보상 처리를 등록합니다.</p>
     *
     * @param command 회원가입 입력값
     * @return 생성된 사용자 정보와 발급된 인증 토큰 정보
     */
    @Transactional
    public SignUpResult signUp(SignUpCommand command) {
        Objects.requireNonNull(
                command,
                "회원가입 명령은 필수입니다."
        );

        String normalizedEmail =
                emailNormalizer.normalize(command.email());

        String emailLookupHash =
                emailLookupHasher.hash(normalizedEmail);

        String normalizedNickname =
                normalizeNickname(command.nickname());

        validateNotDuplicated(
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

        User savedUser = userRepository.save(user);

        UserGamificationState gamificationState =
                UserGamificationState.create(
                        savedUser.getId()
                );

        gamificationStateRepository.save(
                gamificationState
        );

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

        registerAuthSessionRollbackCompensation(
                sessionId
        );

        authSessionStore.save(authSession);

        long accessTokenExpiresIn =
                calculateExpiresInSeconds(
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
     * 이메일 또는 닉네임이 미삭제 사용자와 중복되는지 확인합니다.
     */
    private void validateNotDuplicated(
            String emailLookupHash,
            String nickname
    ) {
        if (userRepository.existsByEmailLookupHash(
                emailLookupHash
        )) {
            throw new BusinessException(
                    ErrorCode.USER_DUPLICATE_EMAIL
            );
        }

        if (nickname != null
                && !nickname.isBlank()
                && userRepository.existsByNicknameIgnoreCase(
                nickname
        )) {
            throw new BusinessException(
                    ErrorCode.USER_DUPLICATE_NICKNAME
            );
        }
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

    /**
     * Access Token 만료 시각을 현재 시각 기준의 남은 초 단위로 변환합니다.
     *
     * <p>밀리초 단위의 일부 시간이 남아 있는 경우 클라이언트가
     * 지나치게 짧은 만료 시간을 받지 않도록 초 단위로 올림 처리합니다.</p>
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

    /**
     * DB 트랜잭션이 최종적으로 커밋되지 못하면
     * 회원가입 과정에서 생성한 Redis 인증 세션을 제거합니다.
     *
     * <p>단위 테스트처럼 Spring 트랜잭션 동기화가 활성화되지 않은 환경에서는
     * 별도의 보상 처리를 등록하지 않습니다.</p>
     *
     * @param sessionId 삭제 대상 인증 세션 식별자
     */
    private void registerAuthSessionRollbackCompensation(
            UUID sessionId
    ) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCompletion(
                                    int status
                            ) {
                                if (status
                                        != TransactionSynchronization
                                        .STATUS_COMMITTED) {
                                    authSessionStore
                                            .deleteBySessionId(
                                                    sessionId
                                            );
                                }
                            }
                        }
                );
    }
}
