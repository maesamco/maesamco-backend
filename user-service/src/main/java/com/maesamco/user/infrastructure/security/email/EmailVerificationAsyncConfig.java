package com.maesamco.user.infrastructure.security.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 이메일 인증 메일 발송에 사용하는 비동기 실행 환경을 구성합니다.
 *
 * <p>SMTP 전송은 블로킹 I/O이므로 HTTP 요청 처리 스레드와 분리하여
 * 이메일 가입 여부 및 메일 발송 시간에 따른 응답 지연 영향을 줄입니다.</p>
 *
 * <p>무제한으로 스레드가 증가하지 않도록 전용 Thread Pool과
 * 제한된 작업 Queue를 사용합니다.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class EmailVerificationAsyncConfig {

    public static final String EMAIL_VERIFICATION_EXECUTOR =
            "emailVerificationTaskExecutor";

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 100;

    /**
     * 이메일 인증 메일 발송 작업 전용 Executor를 생성합니다.
     *
     * <p>Queue가 가득 찬 경우 요청 스레드에서 SMTP 작업을 직접 수행하지 않도록
     * {@link ThreadPoolExecutor.AbortPolicy}를 사용합니다.</p>
     *
     * @return 이메일 인증 전용 비동기 Executor
     */
    @Bean(name = EMAIL_VERIFICATION_EXECUTOR)
    public Executor emailVerificationTaskExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);

        executor.setThreadNamePrefix(
                "email-verification-"
        );

        /*
         * CallerRunsPolicy를 사용하면 Queue 포화 시 HTTP 요청 스레드에서
         * SMTP 작업이 실행될 수 있으므로 사용하지 않습니다.
         */
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        executor.initialize();

        return executor;
    }
}
