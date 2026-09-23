package com.maesamco.content.integration;

import com.maesamco.content.application.command.ProblemUpdateCommand;
import com.maesamco.content.application.command.UpdateField;
import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.finder_service.ProblemFinderService;
import com.maesamco.content.application.persistence_service.ProblemService;
import com.maesamco.content.application.result.ProblemResult;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.config.QuerydslConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.infrastructure.persistence.ProblemCommandRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.ProblemQueryRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.ProblemVersionRepositoryImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
        ProblemService.class,
        ProblemFinderService.class,
        ProblemCommandRepositoryImpl.class,
        ProblemQueryRepositoryImpl.class,
        ProblemVersionRepositoryImpl.class,
        ProblemServiceConcurrencyIntegrationTest.TestConfig.class
})
@EnableJpaRepositories(
        basePackages =
                "com.maesamco.content.infrastructure.persistence"
)
@Transactional(
        propagation = Propagation.NOT_SUPPORTED
)
class ProblemServiceConcurrencyIntegrationTest {

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
    private ProblemService problemService;

    @Test
    @DisplayName(
            "동일한 lockVersion으로 동시에 수정하면 "
                    + "비관적 잠금으로 직렬화되고 한 요청만 성공한다"
    )
    void updateProblem_concurrentRequests_onlyOneSucceeds()
            throws Exception {

        // given
        UUID problemId =
                createProblemFixture();

        Long initialLockVersion =
                readLockVersion(
                        problemId
                );

        assertThat(initialLockVersion)
                .isZero();

        ProblemUpdateCommand firstCommand =
                createUpdateCommand(
                        "첫 번째 동시 수정",
                        initialLockVersion
                );

        ProblemUpdateCommand secondCommand =
                createUpdateCommand(
                        "두 번째 동시 수정",
                        initialLockVersion
                );

        assertThat(
                AopUtils.isAopProxy(
                        problemService
                )
        ).isTrue();

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        try {
            Future<AttemptResult> firstFuture =
                    executor.submit(
                            () -> executeUpdate(
                                    problemId,
                                    firstCommand,
                                    ready,
                                    start
                            )
                    );

            Future<AttemptResult> secondFuture =
                    executor.submit(
                            () -> executeUpdate(
                                    problemId,
                                    secondCommand,
                                    ready,
                                    start
                            )
                    );

            assertThat(
                    ready.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            start.countDown();

            AttemptResult firstResult =
                    firstFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            AttemptResult secondResult =
                    secondFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            List<AttemptResult> results =
                    List.of(
                            firstResult,
                            secondResult
                    );

            // then
            assertThat(results)
                    .filteredOn(
                            AttemptResult::success
                    )
                    .hasSize(1);

            assertThat(results)
                    .filteredOn(
                            result ->
                                    !result.success()
                    )
                    .hasSize(1);

            Throwable failure =
                    results.stream()
                            .filter(
                                    result ->
                                            !result.success()
                            )
                            .map(
                                    AttemptResult::failure
                            )
                            .findFirst()
                            .orElseThrow();

            assertThat(failure)
                    .isInstanceOf(
                            BusinessException.class
                    );

            BusinessException businessException =
                    (BusinessException) failure;

            assertThat(
                    businessException.getErrorCode()
            ).isEqualTo(
                    ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY
            );

            PersistedState persistedState =
                    readPersistedState(
                            problemId
                    );

            assertThat(
                    persistedState.title()
            ).isIn(
                    "첫 번째 동시 수정",
                    "두 번째 동시 수정"
            );

            /*
             * 두 요청 모두 lockVersion=0을 사용했지만
             * 실제 DB 수정은 한 번만 성공해야 합니다.
             */
            assertThat(
                    persistedState.lockVersion()
            ).isEqualTo(1L);

            /*
             * 문제 콘텐츠 버전도 성공한 수정 한 건만 반영됩니다.
             */
            assertThat(
                    persistedState.currentVersionNo()
            ).isEqualTo(2);

            /*
             * 성공한 수정 요청 한 건만
             * ProblemVersion snapshot을 남겨야 합니다.
             */
            assertThat(
                    persistedState.problemVersionCount()
            ).isEqualTo(1L);

        } finally {
            executor.shutdownNow();
        }
    }

    private AttemptResult executeUpdate(
            UUID problemId,
            ProblemUpdateCommand command,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();

        try {
            if (!start.await(
                    5,
                    TimeUnit.SECONDS
            )) {
                return AttemptResult.failure(
                        new IllegalStateException(
                                "동시 실행 시작 대기 시간이 초과되었습니다."
                        )
                );
            }

            ProblemResult result =
                    problemService.updateProblem(
                            problemId,
                            command
                    );

            return AttemptResult.success(
                    result
            );

        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            return AttemptResult.failure(
                    exception
            );

        } catch (Throwable throwable) {
            return AttemptResult.failure(
                    throwable
            );
        }
    }

    private UUID createProblemFixture() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        return transactionTemplate.execute(
                status -> {
                    Problem problem =
                            Problem.create(
                                    "동시성 테스트 문제",
                                    ProgrammingLanguage.JAVA,
                                    ProblemDifficulty.EASY,
                                    ProblemType.CODE,
                                    "동시 수정 테스트",
                                    "public class Main {}",
                                    RunningTimeLimit.SECOND_1,
                                    RunningMemoryLimit.MB_128,
                                    TimerPolicy.APPLY60,
                                    ProblemSource.HUMAN_AUTHORED,
                                    ProblemStatus.REVIEW_PENDING
                            );

                    entityManager.persist(
                            problem
                    );

                    entityManager.flush();

                    return problem.getId();
                }
        );
    }

    private Long readLockVersion(
            UUID problemId
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        return transactionTemplate.execute(
                status -> {
                    entityManager.clear();

                    Problem problem =
                            entityManager.find(
                                    Problem.class,
                                    problemId
                            );

                    return problem.getLockVersion();
                }
        );
    }

    private PersistedState readPersistedState(
            UUID problemId
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        return transactionTemplate.execute(
                status -> {
                    entityManager.clear();

                    Problem problem =
                            entityManager.find(
                                    Problem.class,
                                    problemId
                            );

                    Long versionCount =
                            entityManager.createQuery(
                                            """
                                            SELECT COUNT(problemVersion)
                                            FROM ProblemVersion problemVersion
                                            WHERE problemVersion.problemId
                                                  = :problemId
                                            """,
                                            Long.class
                                    )
                                    .setParameter(
                                            "problemId",
                                            problemId
                                    )
                                    .getSingleResult();

                    return new PersistedState(
                            problem.getTitle(),
                            problem.getLockVersion(),
                            problem.getCurrentVersionNo(),
                            versionCount
                    );
                }
        );
    }

    private ProblemUpdateCommand createUpdateCommand(
            String title,
            Long lockVersion
    ) {
        return new ProblemUpdateCommand(
                title,
                lockVersion,
                null,
                null,
                null,
                null,
                UpdateField.undefined(),
                null,
                null,
                null,
                null,
                UpdateField.undefined()
        );
    }

    private record AttemptResult(
            boolean success,
            ProblemResult result,
            Throwable failure
    ) {

        static AttemptResult success(
                ProblemResult result
        ) {
            return new AttemptResult(
                    true,
                    result,
                    null
            );
        }

        static AttemptResult failure(
                Throwable failure
        ) {
            return new AttemptResult(
                    false,
                    null,
                    failure
            );
        }
    }

    private record PersistedState(
            String title,
            Long lockVersion,
            Integer currentVersionNo,
            long problemVersionCount
    ) {
    }

    @TestConfiguration(
            proxyBeanMethods = false
    )
    static class TestConfig {

        @Bean
        @Primary
        ProblemPublicationFacade
        problemPublicationFacade() {
            return Mockito.mock(
                    ProblemPublicationFacade.class
            );
        }
    }
}
