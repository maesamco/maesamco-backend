package com.maesamco.content.problem.application.service;

import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.config.QuerydslConfig;
import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import com.maesamco.content.problem.domain.enums.ProblemEventOutboxStatus;
import com.maesamco.content.problem.domain.repository.ProblemEventOutboxRepository;
import com.maesamco.content.problem.domain.repository.ProblemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProblemEventOutboxStatusService의 실제 트랜잭션 경계와
 * DB commit 동작을 PostgreSQL 환경에서 검증합니다.
 *
 * <p>테스트 자체는 트랜잭션을 시작하지 않고,
 * Spring AOP Proxy를 통해 호출되는 StatusService의
 * @Transactional만 사용합니다.</p>
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema",
        "spring.data.jpa.repositories.enabled=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(
        FlywayAutoConfiguration.class
)
@Import({
        JpaAuditingConfig.class,
        QuerydslConfig.class,
        ProblemEventOutboxStatusService.class
})
@EnableJpaRepositories(
        basePackageClasses = {
                ProblemEventOutboxRepository.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        ProblemRepository.class
                }
        )
)
@Transactional(
        propagation = Propagation.NOT_SUPPORTED
)
class ProblemEventOutboxStatusServiceIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    static {
        postgres.start();
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ProblemEventOutboxRepository problemEventOutboxRepository;

    @Autowired
    private ProblemEventOutboxStatusService statusService;

    @Test
    @DisplayName(
            "markPublished는 Spring Proxy 트랜잭션에서 Outbox 상태를 DB에 commit한다"
    )
    void markPublished_commitsPublishedStateThroughTransactionalProxy() {
        // given
        UUID outboxId =
                createPendingOutboxFixture();

        Instant publishedAt =
                Instant.parse(
                        "2026-09-08T00:00:10Z"
                );

        assertThat(
                AopUtils.isAopProxy(
                        statusService
                )
        ).isTrue();

        // when
        statusService.markPublished(
                outboxId,
                publishedAt
        );

        // then
        OutboxState result =
                readOutboxState(
                        outboxId
                );

        assertThat(
                result.status()
        ).isEqualTo(
                ProblemEventOutboxStatus.PUBLISHED
        );

        assertThat(
                result.publishedAt()
        ).isEqualTo(
                publishedAt
        );

        assertThat(
                result.retryCount()
        ).isZero();

        assertThat(
                result.lastError()
        ).isNull();
    }

    @Test
    @DisplayName(
            "recordFailure는 Spring Proxy 트랜잭션에서 retryCount와 실패 사유를 DB에 commit한다"
    )
    void recordFailure_commitsFailureStateThroughTransactionalProxy() {
        // given
        UUID outboxId =
                createPendingOutboxFixture();

        String safeError =
                "KAFKA_PUBLISH_TIMEOUT";

        assertThat(
                AopUtils.isAopProxy(
                        statusService
                )
        ).isTrue();

        // when
        statusService.recordFailure(
                outboxId,
                safeError
        );

        // then
        OutboxState result =
                readOutboxState(
                        outboxId
                );

        assertThat(
                result.status()
        ).isEqualTo(
                ProblemEventOutboxStatus.PENDING
        );

        assertThat(
                result.retryCount()
        ).isEqualTo(
                1
        );

        assertThat(
                result.lastError()
        ).isEqualTo(
                safeError
        );

        assertThat(
                result.publishedAt()
        ).isNull();
    }

    @Test
    @DisplayName(
            "markFailed는 Spring Proxy 트랜잭션에서 FAILED 상태를 DB에 commit한다"
    )
    void markFailed_commitsTerminalFailureStateThroughTransactionalProxy() {
        // given
        UUID outboxId =
                createPendingOutboxFixture();

        String safeError =
                "EVENT_PAYLOAD_TOO_LARGE";

        assertThat(
                AopUtils.isAopProxy(
                        statusService
                )
        ).isTrue();

        // when
        statusService.markFailed(
                outboxId,
                safeError
        );

        // then
        OutboxState result =
                readOutboxState(
                        outboxId
                );

        assertThat(result.status())
                .isEqualTo(
                        ProblemEventOutboxStatus.FAILED
                );
        assertThat(result.retryCount())
                .isEqualTo(1);
        assertThat(result.lastError())
                .isEqualTo(safeError);
        assertThat(result.publishedAt())
                .isNull();
    }

    /**
     * 서비스 트랜잭션과 분리된 선행 트랜잭션에서
     * PENDING Outbox를 실제 PostgreSQL에 저장합니다.
     */
    private UUID createPendingOutboxFixture() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        return transactionTemplate.execute(status -> {
            ProblemEventOutbox outbox =
                    ProblemEventOutbox.createPending(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            1,
                            """
                            {
                              "eventType": "PROBLEM_PUBLISHED"
                            }
                            """,
                            Instant.parse(
                                    "2026-09-08T00:00:00Z"
                            )
                    );

            ProblemEventOutbox savedOutbox =
                    problemEventOutboxRepository
                            .saveAndFlush(
                                    outbox
                            );

            return savedOutbox.getId();
        });
    }

    /**
     * StatusService 트랜잭션이 종료된 후 새로운 트랜잭션에서
     * 실제 DB 상태를 다시 조회합니다.
     */
    private OutboxState readOutboxState(
            UUID outboxId
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        return transactionTemplate.execute(status -> {
            entityManager.clear();

            ProblemEventOutbox outbox =
                    problemEventOutboxRepository
                            .findById(
                                    outboxId
                            )
                            .orElseThrow();

            return new OutboxState(
                    outbox.getStatus(),
                    outbox.getRetryCount(),
                    outbox.getLastError(),
                    outbox.getPublishedAt()
            );
        });
    }

    private record OutboxState(
            ProblemEventOutboxStatus status,
            int retryCount,
            String lastError,
            Instant publishedAt
    ) {
    }
}
