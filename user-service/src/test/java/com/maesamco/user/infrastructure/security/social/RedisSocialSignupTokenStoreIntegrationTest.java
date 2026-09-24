package com.maesamco.user.infrastructure.security.social;

import com.maesamco.user.application.port.SocialSignupTicket;
import com.maesamco.user.application.port.SocialSignupTokenStore;
import com.maesamco.user.domain.entity.SocialProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis로 소셜 회원가입 Token의 저장, 사전 조회, 일회성 소비를 검증합니다(#308).
 */
@DataRedisTest
@Import(RedisSocialSignupTokenStore.class)
@Testcontainers
class RedisSocialSignupTokenStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final String KEY_PREFIX =
            "social-signup-token:";

    private static final String TOKEN_HASH =
            "t".repeat(64);

    private static final String KEY =
            KEY_PREFIX + TOKEN_HASH;

    private static final Duration TTL =
            Duration.ofMinutes(10);

    private static final SocialSignupTicket TICKET =
            new SocialSignupTicket(
                    SocialProvider.GOOGLE,
                    "google-sub-123",
                    "e".repeat(64),
                    "encrypted-email"
            );

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7-alpine")
            ).withExposedPorts(REDIS_PORT);

    @Autowired
    private SocialSignupTokenStore socialSignupTokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void configureRedis(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.data.redis.host",
                REDIS::getHost
        );

        registry.add(
                "spring.data.redis.port",
                () -> REDIS.getMappedPort(REDIS_PORT)
        );
    }

    @BeforeEach
    void deleteSocialSignupKeys() {
        Set<String> keys =
                redisTemplate.keys(KEY_PREFIX + "*");

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("Token 원문 없이 해시 Key에 귀속 정보를 저장하고 TTL을 함께 설정한다")
    void save_storesTicketWithTtl() {
        // when
        socialSignupTokenStore.save(TOKEN_HASH, TICKET, TTL);

        // then
        assertThat(redisTemplate.type(KEY).code()).isEqualTo("hash");
        assertThat(redisTemplate.<String, String>opsForHash().entries(KEY))
                .containsEntry("provider", "GOOGLE")
                .containsEntry("providerUserId", "google-sub-123")
                .containsEntry("emailLookupHash", "e".repeat(64))
                .containsEntry("encryptedEmail", "encrypted-email");

        Long ttlSeconds = redisTemplate.getExpire(KEY, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isPositive().isLessThanOrEqualTo(TTL.toSeconds());
    }

    @Test
    @DisplayName("find는 Token을 소비하지 않고 귀속 정보를 반환한다")
    void find_doesNotConsume() {
        // given
        socialSignupTokenStore.save(TOKEN_HASH, TICKET, TTL);

        // when
        Optional<SocialSignupTicket> first = socialSignupTokenStore.find(TOKEN_HASH);
        Optional<SocialSignupTicket> second = socialSignupTokenStore.find(TOKEN_HASH);

        // then
        assertThat(first).contains(TICKET);
        assertThat(second).contains(TICKET);
        assertThat(redisTemplate.hasKey(KEY)).isTrue();
    }

    @Test
    @DisplayName("consume은 귀속 정보를 반환하고 Token을 삭제하므로 두 번째 소비는 실패한다")
    void consume_onlyOnce() {
        // given
        socialSignupTokenStore.save(TOKEN_HASH, TICKET, TTL);

        // when
        Optional<SocialSignupTicket> first = socialSignupTokenStore.consume(TOKEN_HASH);
        Optional<SocialSignupTicket> second = socialSignupTokenStore.consume(TOKEN_HASH);

        // then
        assertThat(first).contains(TICKET);
        assertThat(second).isEmpty();
        assertThat(socialSignupTokenStore.find(TOKEN_HASH)).isEmpty();
        assertThat(redisTemplate.hasKey(KEY)).isFalse();
    }

    @Test
    @DisplayName("존재하지 않거나 만료된 Token은 조회·소비 모두 empty를 반환한다")
    void unknownToken_returnsEmpty() {
        assertThat(socialSignupTokenStore.find(TOKEN_HASH)).isEmpty();
        assertThat(socialSignupTokenStore.consume(TOKEN_HASH)).isEmpty();
    }

    @Test
    @DisplayName("같은 Token으로 동시에 소비해도 한 요청만 귀속 정보를 받는다")
    void consume_concurrently_onlyOneSucceeds() throws Exception {
        // given
        socialSignupTokenStore.save(TOKEN_HASH, TICKET, TTL);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Future<Optional<SocialSignupTicket>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    startLatch.await();
                    return socialSignupTokenStore.consume(TOKEN_HASH);
                }));
            }

            // when
            startLatch.countDown();

            int successCount = 0;
            for (Future<Optional<SocialSignupTicket>> future : futures) {
                if (future.get(5, TimeUnit.SECONDS).isPresent()) {
                    successCount++;
                }
            }

            // then
            assertThat(successCount).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("이전 버전(#304)이 문자열로 저장한 Token은 유효하지 않은 Token으로 처리하고 소비 시 삭제한다")
    void legacyStringValue_isTreatedAsInvalid() {
        // given — #304는 Value로 이메일 조회 해시 문자열만 저장했다.
        redisTemplate.opsForValue().set(KEY, "e".repeat(64), TTL);

        // when & then
        assertThat(socialSignupTokenStore.find(TOKEN_HASH)).isEmpty();
        assertThat(socialSignupTokenStore.consume(TOKEN_HASH)).isEmpty();
        assertThat(redisTemplate.hasKey(KEY)).isFalse();
    }

    @Test
    @DisplayName("필드가 누락되거나 Provider 값이 잘못된 Token은 유효하지 않은 Token으로 처리한다")
    void corruptedTicket_isTreatedAsInvalid() {
        // given
        redisTemplate.opsForHash().put(KEY, "provider", "UNKNOWN_PROVIDER");
        redisTemplate.opsForHash().put(KEY, "providerUserId", "google-sub-123");
        redisTemplate.opsForHash().put(KEY, "emailLookupHash", "e".repeat(64));
        redisTemplate.opsForHash().put(KEY, "encryptedEmail", "encrypted-email");

        String missingFieldKey = KEY_PREFIX + "m".repeat(64);
        redisTemplate.opsForHash().put(missingFieldKey, "provider", "GOOGLE");

        // when & then
        assertThat(socialSignupTokenStore.find(TOKEN_HASH)).isEmpty();
        assertThat(socialSignupTokenStore.find("m".repeat(64))).isEmpty();
    }
}
