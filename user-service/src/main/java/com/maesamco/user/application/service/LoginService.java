package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
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
import com.maesamco.user.global.security.TokenExpirationCalculator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
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

    private static final String LOGIN_METRIC_NAME =
            "user.auth.login";

    private static final String LOGIN_RESULT_TAG =
            "result";

    private static final String LOGIN_RESULT_SUCCESS =
            "success";

    private static final String LOGIN_RESULT_INVALID_CREDENTIALS =
            "invalid_credentials";

    private static final String LOGIN_RESULT_INACTIVE_USER =
            "inactive_user";

    /**
     * 존재하지 않는 사용자 로그인에서도 실제 비밀번호 검증과
     * 유사한 Argon2id 연산 비용을 발생시키기 위한 더미 해시입니다.
     *
     * <p>현재 PasswordEncoderConfig에서 사용하는
     * Argon2PasswordEncoder Spring Security 5.8 기본 설정과
     * 동일한 파라미터 형식을 사용합니다.</p>
     *
     * <p>더미 비교 결과는 인증 판단에 절대 사용하지 않습니다.</p>
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$"
                    + "lcXwO0AWY0tG92eew5K4Eg$"
                    + "bBmYQrH4yfKc8V4FqDeuehlyCV+i+FALyHg+3qUUypM";

    private final EmailNormalizer emailNormalizer;
    private final EmailLookupHasher emailLookupHasher;
    private final PasswordHasher passwordHasher;
    private final UserRepository userRepository;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthSessionStore authSessionStore;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    /**
     * 이메일과 비밀번호를 검증하고 새로운 로그인 인증 세션을 생성합니다.
     *
     * <p>존재하지 않는 사용자와 비밀번호 불일치는
     * 사용자 존재 여부가 외부에 노출되지 않도록
     * 동일한 {@link ErrorCode#INVALID_CREDENTIALS}로 처리합니다.</p>
     *
     * <p>존재하지 않는 사용자의 경우에도 더미 Argon2id 해시에 대해
     * 비밀번호 비교를 수행하여 사용자 존재 여부에 따른
     * 비밀번호 검증 비용 차이를 줄입니다.</p>
     *
     * <p>로그인 성공과 인증 실패는 Micrometer Counter로 기록합니다.
     * 이메일, 비밀번호, userId와 같은 사용자별 식별값은
     * Metric Tag로 사용하지 않습니다.</p>
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
                .orElse(null);

        if (user == null) {
            consumePasswordVerificationCost(
                    command.password()
            );

            incrementLoginMetric(
                    LOGIN_RESULT_INVALID_CREDENTIALS
            );

            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }

        validatePassword(
                command.password(),
                user.getPasswordHash()
        );

        validateActiveUser(user);

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
                TokenExpirationCalculator.remainingSeconds(
                        now,
                        issuedTokens.accessTokenExpiresAt()
                );

        incrementLoginMetric(
                LOGIN_RESULT_SUCCESS
        );

        return new LoginResult(
                user.getId(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                user.getJavaExperienceMonths(),
                user.getLearningLevel(),
                issuedTokens.accessToken(),
                accessTokenExpiresIn,
                issuedTokens
        );
    }

    /**
     * 존재하지 않는 사용자에 대해서도 실제 로그인과 유사하게
     * Argon2id 비밀번호 비교 연산을 한 번 수행합니다.
     *
     * <p>더미 해시와의 비교 결과는 인증 판단에 사용하지 않습니다.</p>
     *
     * @param rawPassword 로그인 요청의 비밀번호 원문
     */
    private void consumePasswordVerificationCost(
            String rawPassword
    ) {
        passwordHasher.matches(
                rawPassword,
                DUMMY_PASSWORD_HASH
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
            incrementLoginMetric(
                    LOGIN_RESULT_INVALID_CREDENTIALS
            );

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
            incrementLoginMetric(
                    LOGIN_RESULT_INACTIVE_USER
            );

            throw new BusinessException(
                    ErrorCode.USER_NOT_ACTIVE
            );
        }
    }

    /**
     * 로그인 결과를 Micrometer Counter에 기록합니다.
     *
     * <p>result는 정해진 소수의 값만 사용하여
     * Metric Cardinality가 사용자 수에 따라 증가하지 않도록 합니다.</p>
     *
     * @param result 로그인 처리 결과
     */
    private void incrementLoginMetric(
            String result
    ) {
        meterRegistry
                .counter(
                        LOGIN_METRIC_NAME,
                        LOGIN_RESULT_TAG,
                        result
                )
                .increment();
    }
}
