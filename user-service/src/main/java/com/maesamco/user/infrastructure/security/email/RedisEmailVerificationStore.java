package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.port.EmailVerificationStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * Redis를 이용해 이메일 인증 상태를 저장하고 원자적으로 변경합니다.
 *
 * <p>동일 이메일에 속한 Redis Key는 {@code {emailLookupHash}}를
 * 동일한 hash tag로 사용합니다. 따라서 Redis Cluster 환경에서도
 * 여러 Key를 사용하는 Lua Script를 같은 hash slot에서 실행할 수 있습니다.</p>
 *
 * <p>Redis Key에는 이메일 원문을 사용하지 않고 이메일 조회용
 * HMAC hash만 사용합니다.</p>
 */
@Repository
public class RedisEmailVerificationStore
        implements EmailVerificationStore {

    private static final String KEY_PREFIX =
            "email-verification:";

    private static final long CHALLENGE_CREATED = 1L;
    private static final long CHALLENGE_COOLDOWN_ACTIVE = 2L;
    private static final long CHALLENGE_RATE_LIMIT_EXCEEDED = 3L;

    private static final long CONFIRM_VERIFIED = 1L;
    private static final long CONFIRM_INVALID_CODE = 2L;
    private static final long CONFIRM_EXPIRED = 3L;
    private static final long CONFIRM_ATTEMPTS_EXCEEDED = 4L;

    private static final long TOKEN_NOT_CONSUMED = 0L;
    private static final long TOKEN_CONSUMED = 1L;

    /**
     * 이메일 인증 challenge 생성, 재전송 cooldown 확인,
     * 요청 횟수 제한 적용을 원자적으로 수행합니다.
     *
     * <p>반환값:</p>
     * <ul>
     *     <li>1: challenge 생성 완료</li>
     *     <li>2: 재전송 cooldown 활성 상태</li>
     *     <li>3: 요청 횟수 제한 초과</li>
     * </ul>
     */
    private static final DefaultRedisScript<Long>
            CREATE_CHALLENGE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('EXISTS', KEYS[2]) == 1 then
                        return 2
                    end

                    local requestCountValue =
                        redis.call('GET', KEYS[3])

                    local maxRequests =
                        tonumber(ARGV[5])

                    if requestCountValue then
                        local requestCount =
                            tonumber(requestCountValue)

                        if requestCount >= maxRequests then
                            return 3
                        end

                        redis.call('INCR', KEYS[3])
                    else
                        redis.call(
                            'PSETEX',
                            KEYS[3],
                            ARGV[4],
                            '1'
                        )
                    end

                    redis.call(
                        'HSET',
                        KEYS[1],
                        'verificationCodeHash',
                        ARGV[1],
                        'attempts',
                        '0'
                    )

                    redis.call(
                        'PEXPIRE',
                        KEYS[1],
                        ARGV[2]
                    )

                    redis.call(
                        'PSETEX',
                        KEYS[2],
                        ARGV[3],
                        '1'
                    )

                    return 1
                    """,
                    Long.class
            );

    /**
     * 인증 코드 확인과 실패 횟수 증가,
     * 성공 시 signup token 발급을 원자적으로 수행합니다.
     *
     * <p>잘못된 인증 코드가 입력되면 challenge의 TTL은 연장하지 않고
     * 실패 횟수만 증가시킵니다. 최대 실패 횟수에 도달하면 challenge를
     * 즉시 제거합니다.</p>
     *
     * <p>반환값:</p>
     * <ul>
     *     <li>1: 인증 성공 및 signup token 발급</li>
     *     <li>2: 인증 코드 불일치</li>
     *     <li>3: challenge 만료 또는 없음</li>
     *     <li>4: 최대 인증 실패 횟수 초과</li>
     * </ul>
     */
    private static final DefaultRedisScript<Long>
            CONFIRM_AND_ISSUE_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return 3
                    end

                    local storedHash =
                        redis.call(
                            'HGET',
                            KEYS[1],
                            'verificationCodeHash'
                        )

                    if not storedHash then
                        redis.call('DEL', KEYS[1])
                        return 3
                    end

                    if storedHash ~= ARGV[1] then
                        local attemptsValue =
                            redis.call(
                                'HGET',
                                KEYS[1],
                                'attempts'
                            )

                        local attempts = 0

                        if attemptsValue then
                            attempts =
                                tonumber(attemptsValue)
                        end

                        attempts = attempts + 1

                        if attempts >= tonumber(ARGV[3]) then
                            redis.call('DEL', KEYS[1])
                            return 4
                        end

                        redis.call(
                            'HSET',
                            KEYS[1],
                            'attempts',
                            tostring(attempts)
                        )

                        return 2
                    end

                    redis.call('DEL', KEYS[1])

                    redis.call(
                        'PSETEX',
                        KEYS[2],
                        ARGV[2],
                        ARGV[4]
                    )

                    return 1
                    """,
                    Long.class
            );

    /**
     * signup token의 이메일 귀속 관계를 확인하고
     * 성공한 경우 토큰을 원자적으로 제거합니다.
     *
     * <p>반환값:</p>
     * <ul>
     *     <li>0: 토큰 없음 또는 이메일 불일치</li>
     *     <li>1: 토큰 소비 성공</li>
     * </ul>
     */
    private static final DefaultRedisScript<Long>
            CONSUME_SIGNUP_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local storedEmailLookupHash =
                        redis.call('GET', KEYS[1])

                    if not storedEmailLookupHash then
                        return 0
                    end

                    if storedEmailLookupHash ~= ARGV[1] then
                        return 0
                    end

                    redis.call('DEL', KEYS[1])

                    return 1
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;

    /**
     * Redis 이메일 인증 저장소를 생성합니다.
     *
     * @param redisTemplate 문자열 기반 Redis 접근 객체
     */
    public RedisEmailVerificationStore(
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChallengeCreationResult createChallenge(
            String emailLookupHash,
            String verificationCodeHash,
            Duration challengeTtl,
            Duration resendCooldown,
            Duration requestLimitWindow,
            int maxRequestsPerWindow
    ) {
        requireHash(
                emailLookupHash,
                "이메일 조회 hash는 필수입니다."
        );
        requireHash(
                verificationCodeHash,
                "이메일 인증 코드 hash는 필수입니다."
        );

        requirePositiveDuration(
                challengeTtl,
                "인증 challenge TTL"
        );
        requirePositiveDuration(
                resendCooldown,
                "재전송 cooldown"
        );
        requirePositiveDuration(
                requestLimitWindow,
                "인증 요청 제한 기간"
        );
        requirePositiveCount(
                maxRequestsPerWindow,
                "기간당 최대 인증 요청 횟수"
        );

        Long result = redisTemplate.execute(
                CREATE_CHALLENGE_SCRIPT,
                List.of(
                        createChallengeKey(emailLookupHash),
                        createCooldownKey(emailLookupHash),
                        createRequestCountKey(emailLookupHash)
                ),
                verificationCodeHash,
                Long.toString(challengeTtl.toMillis()),
                Long.toString(resendCooldown.toMillis()),
                Long.toString(requestLimitWindow.toMillis()),
                Integer.toString(maxRequestsPerWindow)
        );

        if (result == null) {
            throw new IllegalStateException(
                    "이메일 인증 challenge 생성 결과를 확인할 수 없습니다."
            );
        }

        if (result == CHALLENGE_CREATED) {
            return ChallengeCreationResult.CREATED;
        }

        if (result == CHALLENGE_COOLDOWN_ACTIVE) {
            return ChallengeCreationResult.COOLDOWN_ACTIVE;
        }

        if (result == CHALLENGE_RATE_LIMIT_EXCEEDED) {
            return ChallengeCreationResult.RATE_LIMIT_EXCEEDED;
        }

        throw new IllegalStateException(
                "알 수 없는 이메일 인증 challenge 생성 결과입니다: "
                        + result
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConfirmationResult confirmAndIssueSignupToken(
            String emailLookupHash,
            String verificationCodeHash,
            String signupTokenHash,
            Duration signupTokenTtl,
            int maxVerificationAttempts
    ) {
        requireHash(
                emailLookupHash,
                "이메일 조회 hash는 필수입니다."
        );
        requireHash(
                verificationCodeHash,
                "이메일 인증 코드 hash는 필수입니다."
        );
        requireHash(
                signupTokenHash,
                "회원가입 인증 토큰 hash는 필수입니다."
        );

        requirePositiveDuration(
                signupTokenTtl,
                "회원가입 인증 토큰 TTL"
        );
        requirePositiveCount(
                maxVerificationAttempts,
                "최대 인증 시도 횟수"
        );

        Long result = redisTemplate.execute(
                CONFIRM_AND_ISSUE_TOKEN_SCRIPT,
                List.of(
                        createChallengeKey(emailLookupHash),
                        createSignupTokenKey(
                                emailLookupHash,
                                signupTokenHash
                        )
                ),
                verificationCodeHash,
                Long.toString(signupTokenTtl.toMillis()),
                Integer.toString(maxVerificationAttempts),
                emailLookupHash
        );

        if (result == null) {
            throw new IllegalStateException(
                    "이메일 인증 확인 결과를 확인할 수 없습니다."
            );
        }

        if (result == CONFIRM_VERIFIED) {
            return ConfirmationResult.VERIFIED;
        }

        if (result == CONFIRM_INVALID_CODE) {
            return ConfirmationResult.INVALID_CODE;
        }

        if (result == CONFIRM_EXPIRED) {
            return ConfirmationResult.EXPIRED;
        }

        if (result == CONFIRM_ATTEMPTS_EXCEEDED) {
            return ConfirmationResult.ATTEMPTS_EXCEEDED;
        }

        throw new IllegalStateException(
                "알 수 없는 이메일 인증 확인 결과입니다: "
                        + result
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean consumeSignupToken(
            String signupTokenHash,
            String emailLookupHash
    ) {
        requireHash(
                signupTokenHash,
                "회원가입 인증 토큰 hash는 필수입니다."
        );
        requireHash(
                emailLookupHash,
                "이메일 조회 hash는 필수입니다."
        );

        Long result = redisTemplate.execute(
                CONSUME_SIGNUP_TOKEN_SCRIPT,
                List.of(
                        createSignupTokenKey(
                                emailLookupHash,
                                signupTokenHash
                        )
                ),
                emailLookupHash
        );

        if (result == null) {
            throw new IllegalStateException(
                    "회원가입 인증 토큰 소비 결과를 확인할 수 없습니다."
            );
        }

        if (result == TOKEN_CONSUMED) {
            return true;
        }

        if (result == TOKEN_NOT_CONSUMED) {
            return false;
        }

        throw new IllegalStateException(
                "알 수 없는 회원가입 인증 토큰 소비 결과입니다: "
                        + result
        );
    }

    /**
     * hash 필수값을 검증합니다.
     */
    private static void requireHash(
            String hash,
            String message
    ) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Duration 정책값이 양수인지 검증합니다.
     */
    private static void requirePositiveDuration(
            Duration duration,
            String fieldName
    ) {
        if (duration == null
                || duration.isZero()
                || duration.isNegative()) {
            throw new IllegalArgumentException(
                    fieldName + "은 0보다 커야 합니다."
            );
        }
    }

    /**
     * 횟수 정책값이 양수인지 검증합니다.
     */
    private static void requirePositiveCount(
            int count,
            String fieldName
    ) {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    fieldName + "는 1 이상이어야 합니다."
            );
        }
    }

    /**
     * 인증 challenge Redis Key를 생성합니다.
     */
    private static String createChallengeKey(
            String emailLookupHash
    ) {
        return createEmailKeyPrefix(emailLookupHash)
                + ":challenge";
    }

    /**
     * 재전송 cooldown Redis Key를 생성합니다.
     */
    private static String createCooldownKey(
            String emailLookupHash
    ) {
        return createEmailKeyPrefix(emailLookupHash)
                + ":cooldown";
    }

    /**
     * 인증 요청 횟수 Redis Key를 생성합니다.
     */
    private static String createRequestCountKey(
            String emailLookupHash
    ) {
        return createEmailKeyPrefix(emailLookupHash)
                + ":request-count";
    }

    /**
     * 회원가입 인증 토큰 Redis Key를 생성합니다.
     */
    private static String createSignupTokenKey(
            String emailLookupHash,
            String signupTokenHash
    ) {
        return createEmailKeyPrefix(emailLookupHash)
                + ":signup-token:"
                + signupTokenHash;
    }

    /**
     * 동일 이메일에 속한 Key들이 Redis Cluster에서
     * 같은 hash slot을 사용하도록 공통 prefix를 생성합니다.
     */
    private static String createEmailKeyPrefix(
            String emailLookupHash
    ) {
        return KEY_PREFIX
                + "{"
                + emailLookupHash
                + "}";
    }
}
