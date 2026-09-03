package com.maesamco.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RateLimitFilter가 요구하는 ReactiveRedisTemplate<String, Long> Bean.
 * Spring Boot가 ReactiveRedisConnectionFactory는 자동 구성해주지만,
 * <String, Long> 제네릭 조합의 템플릿까지는 자동으로 안 만들어줘서 직접 등록한다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Long> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericToStringSerializer<Long> valueSerializer = new GenericToStringSerializer<>(Long.class);

        RedisSerializationContext<String, Long> context = RedisSerializationContext
                .<String, Long>newSerializationContext(keySerializer)
                .value(valueSerializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}