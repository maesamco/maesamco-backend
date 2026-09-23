package com.maesamco.user.infrastructure.security.social;

import com.maesamco.user.application.port.SocialSignupTokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * Redis 기반 소셜 회원가입 일회성 Token 저장소입니다.
 *
 * <p>Redis에는 Token 원문을 저장하지 않습니다.
 * Token 해시를 Key로 사용하고 인증된 이메일 조회 해시만 Value로 저장합니다.</p>
 */
@Repository
public class RedisSocialSignupTokenStore
        implements SocialSignupTokenStore {

    private static final String KEY_PREFIX =
            "social-signup-token:";

    private static final long TOKEN_NOT_CONSUMED = 0L;
    private static final long TOKEN_CONSUMED = 1L;

    /**
     * Token의 이메일 귀속 관계를 확인한 뒤
     * 성공한 경우 원자적으로 Token을 제거합니다.
     */
    private static final DefaultRedisScript<Long>
            CONSUME_TOKEN_SCRIPT =
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

    public RedisSocialSignupTokenStore(
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(
            String tokenHash,
            String emailLookupHash,
            Duration ttl
    ) {
        requireText(
                tokenHash,
                "소셜 회원가입 Token 해시는 필수입니다."
        );

        requireText(
                emailLookupHash,
                "이메일 조회 해시는 필수입니다."
        );

        if (
                ttl == null
                        || ttl.isZero()
                        || ttl.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "소셜 회원가입 Token TTL은 0보다 커야 합니다."
            );
        }

        redisTemplate.opsForValue()
                .set(
                        createKey(tokenHash),
                        emailLookupHash,
                        ttl
                );
    }

    @Override
    public boolean consume(
            String tokenHash,
            String emailLookupHash
    ) {
        requireText(
                tokenHash,
                "소셜 회원가입 Token 해시는 필수입니다."
        );

        requireText(
                emailLookupHash,
                "이메일 조회 해시는 필수입니다."
        );

        Long result =
                redisTemplate.execute(
                        CONSUME_TOKEN_SCRIPT,
                        List.of(
                                createKey(tokenHash)
                        ),
                        emailLookupHash
                );

        if (result == null) {
            throw new IllegalStateException(
                    "소셜 회원가입 Token 소비 결과를 확인할 수 없습니다."
            );
        }

        if (result == TOKEN_CONSUMED) {
            return true;
        }

        if (result == TOKEN_NOT_CONSUMED) {
            return false;
        }

        throw new IllegalStateException(
                "알 수 없는 소셜 회원가입 Token 소비 결과입니다: "
                        + result
        );
    }

    private static String createKey(
            String tokenHash
    ) {
        return KEY_PREFIX + tokenHash;
    }

    private static void requireText(
            String value,
            String message
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    message
            );
        }
    }
}
