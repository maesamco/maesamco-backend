package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.global.security.hmac.HmacSigningFeignInterceptor;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR #127 심층 재검토(2026-09-09)에서 발견한 P0 회귀 방지 테스트 — JudgeServiceFeignConfig/
 * ContentServiceFeignConfig가 다시 @Configuration으로 component scan에 걸리면, Spring
 * Cloud OpenFeign의 NamedContextFactory#getInstances()가 ancestor(부모) 컨텍스트 빈까지
 * 포함해서 조회하는 바람에 두 Feign Client가 서로의 HMAC RequestInterceptor를 상속하게
 * 된다. 이 테스트는 실제 @EnableFeignClients로 두 Client를 함께 띄운 뒤, 각 Client 전용
 * child ApplicationContext(FeignClientFactory)에서 RequestInterceptor/ErrorDecoder가
 * 정확히 자기 것 하나만 있는지 직접 확인한다 — Mockito로 FeignClient 자체를 대체하는
 * 테스트로는 이 child context 격리 여부를 원천적으로 검증할 수 없다.
 */
@SpringBootTest(classes = {FeignClientConfigIsolationTest.TestConfig.class})
@TestPropertySource(properties = {
        // src/test/resources/application.yml이 src/main/resources/application.yml을
        // 클래스패스에서 완전히 가려서(같은 경로, 병합 안 됨) spring.application.name도
        // 여기서 직접 채워야 한다(PR #127 심층 재검토, 2026-09-09 — 실제 확인).
        "spring.application.name=coaching-service",
        "internal.hmac.outbound.judge-service=test-secret-for-judge",
        "internal.hmac.outbound.content-service=test-secret-for-content"
})
class FeignClientConfigIsolationTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            KafkaAutoConfiguration.class,
            DataRedisAutoConfiguration.class,
            DataRedisReactiveAutoConfiguration.class,
            AnthropicChatAutoConfiguration.class,
            GoogleGenAiChatAutoConfiguration.class
    })
    @EnableFeignClients(clients = {JudgeServiceFeignClient.class, ContentServiceFeignClient.class})
    static class TestConfig {
    }

    @Autowired
    private FeignClientFactory feignClientFactory;

    @Test
    void judge_클라이언트는_자기_HMAC_인터셉터만_갖는다() {
        Map<String, RequestInterceptor> interceptors =
                feignClientFactory.getInstances("judge-service", RequestInterceptor.class);

        assertThat(interceptors).hasSize(1);
        assertThat(interceptors.values().iterator().next())
                .isInstanceOf(HmacSigningFeignInterceptor.class);
    }

    @Test
    void content_클라이언트는_자기_HMAC_인터셉터만_갖는다() {
        Map<String, RequestInterceptor> interceptors =
                feignClientFactory.getInstances("content-service", RequestInterceptor.class);

        assertThat(interceptors).hasSize(1);
        assertThat(interceptors.values().iterator().next())
                .isInstanceOf(HmacSigningFeignInterceptor.class);
    }

    @Test
    void judge와_content의_인터셉터_인스턴스는_서로_다르다() {
        RequestInterceptor judgeInterceptor = feignClientFactory
                .getInstances("judge-service", RequestInterceptor.class)
                .values().iterator().next();
        RequestInterceptor contentInterceptor = feignClientFactory
                .getInstances("content-service", RequestInterceptor.class)
                .values().iterator().next();

        assertThat(judgeInterceptor).isNotSameAs(contentInterceptor);
    }

    @Test
    void judge와_content_클라이언트는_각자_자기_ErrorDecoder만_갖는다() {
        // JudgeServiceAdapter도 ContentServiceAdapter와 동일한 구조적 결함(PR #127 리뷰,
        // 용현님 지적)을 갖고 있어 JudgeServiceErrorDecoder를 추가했다 — 이제 judge도
        // 전용 ErrorDecoder를 갖는다. child ApplicationContext 격리가 유지되는지(서로
        // 다른 타입/인스턴스인지)만 여기서 확인한다.
        ErrorDecoder judgeDecoder = feignClientFactory.getInstance("judge-service", ErrorDecoder.class);
        ErrorDecoder contentDecoder = feignClientFactory.getInstance("content-service", ErrorDecoder.class);

        assertThat(judgeDecoder).isInstanceOf(JudgeServiceErrorDecoder.class);
        assertThat(contentDecoder).isInstanceOf(ContentServiceErrorDecoder.class);
        assertThat(judgeDecoder).isNotSameAs(contentDecoder);
    }
}
