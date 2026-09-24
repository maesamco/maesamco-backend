package com.maesamco.user.infrastructure.security.social;

import com.maesamco.user.application.port.SocialSignupTicket;
import com.maesamco.user.application.port.SocialSignupTokenStore;
import com.maesamco.user.domain.entity.SocialProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Redis 기반 소셜 회원가입 일회성 Token 저장소입니다.
 *
 * <p>Redis에는 Token 원문을 저장하지 않습니다.
 * Token 해시를 Key로 사용하고, Value는 Hash 자료구조로
 * {@link SocialSignupTicket}(Provider, Provider 사용자 ID, 이메일 조회 해시, 이메일 암호문)을 저장합니다(#308).</p>
 *
 * <p>저장·조회·소비는 모두 Lua 스크립트로 원자적으로 수행합니다.
 * 이전 버전(#304)이 문자열로 저장한 Key가 남아 있으면 유효하지 않은 Token으로 취급합니다.</p>
 */
@Repository
public class RedisSocialSignupTokenStore
        implements SocialSignupTokenStore {

    private static final String KEY_PREFIX =
            "social-signup-token:";

    private static final String FIELD_PROVIDER = "provider";
    private static final String FIELD_PROVIDER_USER_ID = "providerUserId";
    private static final String FIELD_EMAIL_LOOKUP_HASH = "emailLookupHash";
    private static final String FIELD_ENCRYPTED_EMAIL = "encryptedEmail";

    /**
     * 기존 Key를 지우고 Hash 필드와 TTL을 한 번에 설정합니다.
     * HSET과 PEXPIRE 사이에 장애가 나서 TTL 없는 Token이 남는 것을 막습니다.
     */
    private static final DefaultRedisScript<Long> SAVE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    redis.call('DEL', KEYS[1])
                    redis.call('HSET', KEYS[1],
                        'provider', ARGV[2],
                        'providerUserId', ARGV[3],
                        'emailLookupHash', ARGV[4],
                        'encryptedEmail', ARGV[5])
                    redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    return 1
                    """,
                    Long.class
            );

    /**
     * Token을 삭제하지 않고 귀속 정보를 조회합니다.
     */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> FIND_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('TYPE', KEYS[1]).ok ~= 'hash' then
                        return {}
                    end

                    return redis.call('HMGET', KEYS[1],
                        'provider', 'providerUserId', 'emailLookupHash', 'encryptedEmail')
                    """,
                    List.class
            );

    /**
     * 귀속 정보를 읽고 같은 스크립트 안에서 Token을 삭제합니다.
     * 동일 Token으로 동시에 요청해도 하나의 요청만 정보를 받습니다.
     */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CONSUME_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local keyType = redis.call('TYPE', KEYS[1]).ok

                    if keyType == 'none' then
                        return {}
                    end

                    if keyType ~= 'hash' then
                        redis.call('DEL', KEYS[1])
                        return {}
                    end

                    local values = redis.call('HMGET', KEYS[1],
                        'provider', 'providerUserId', 'emailLookupHash', 'encryptedEmail')

                    redis.call('DEL', KEYS[1])

                    return values
                    """,
                    List.class
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
            SocialSignupTicket ticket,
            Duration ttl
    ) {
        requireText(
                tokenHash,
                "소셜 회원가입 Token 해시는 필수입니다."
        );

        if (ticket == null) {
            throw new IllegalArgumentException(
                    "소셜 회원가입 Token 귀속 정보는 필수입니다."
            );
        }

        if (
                ttl == null
                        || ttl.isZero()
                        || ttl.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "소셜 회원가입 Token TTL은 0보다 커야 합니다."
            );
        }

        redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(createKey(tokenHash)),
                String.valueOf(ttl.toMillis()),
                ticket.provider().name(),
                ticket.providerUserId(),
                ticket.emailLookupHash(),
                ticket.encryptedEmail()
        );
    }

    @Override
    public Optional<SocialSignupTicket> find(
            String tokenHash
    ) {
        requireText(
                tokenHash,
                "소셜 회원가입 Token 해시는 필수입니다."
        );

        return toTicket(
                redisTemplate.execute(
                        FIND_SCRIPT,
                        List.of(createKey(tokenHash))
                )
        );
    }

    @Override
    public Optional<SocialSignupTicket> consume(
            String tokenHash
    ) {
        requireText(
                tokenHash,
                "소셜 회원가입 Token 해시는 필수입니다."
        );

        return toTicket(
                redisTemplate.execute(
                        CONSUME_SCRIPT,
                        List.of(createKey(tokenHash))
                )
        );
    }

    /**
     * Lua 스크립트 결과(HMGET 순서)를 {@link SocialSignupTicket}으로 변환합니다.
     *
     * <p>필드가 누락됐거나 값이 올바르지 않으면 위·변조되거나 손상된 Token으로 보고 empty를 반환합니다.</p>
     */
    private static Optional<SocialSignupTicket> toTicket(
            List<?> values
    ) {
        if (values == null || values.size() != 4) {
            return Optional.empty();
        }

        for (Object value : values) {
            if (!(value instanceof String text) || text.isBlank()) {
                return Optional.empty();
            }
        }

        try {
            return Optional.of(
                    new SocialSignupTicket(
                            SocialProvider.valueOf((String) values.get(0)),
                            (String) values.get(1),
                            (String) values.get(2),
                            (String) values.get(3)
                    )
            );
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
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
