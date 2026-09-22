package com.maesamco.content.integration;

import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.application.finder.TestCaseFinder;
import com.maesamco.content.application.port.ProblemPublishedEventData;
import com.maesamco.content.application.port.ProblemPublishedEventPort;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.TestCaseStatus;
import com.maesamco.content.domain.entity.problem.*;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.config.QuerydslConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.infrastructure.adapter.event.ProblemPublishedEventAdapter;
import com.maesamco.content.infrastructure.messaging.event.ProblemPublishedEvent;
import com.maesamco.content.infrastructure.persistence.ProblemEventOutboxRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.ProblemVersionRepositoryImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema",
        "spring.data.jpa.repositories.enabled=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        JpaAuditingConfig.class,
        QuerydslConfig.class,
        ProblemPublicationFacade.class,
        ProblemVersionRepositoryImpl.class,
        ProblemEventOutboxRepositoryImpl.class,
        ProblemPublishedEventAdapter.class,
        ProblemPublicationTransactionIntegrationTest.TestConfig.class
})
@EnableJpaRepositories(
        basePackages = {
                "com.maesamco.content.infrastructure.persistence"
        }
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProblemPublicationTransactionIntegrationTest {

    private static final String FORCED_FAILURE_MESSAGE =
            "forced outbox failure";

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
    private ProblemPublicationFacade problemPublicationFacade;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private FailingAfterOutboxFlushPort failingAfterOutboxFlushPort;


    @Test
    @DisplayName(
            "Outbox 저장 이후 실패하면 문제 상태, 버전 증가, ProblemVersion, Outbox가 모두 롤백된다"
    )
    void approvePublication_outboxFailure_rollsBackEntireTransaction()
            throws Exception {

        // given
        UUID problemId =
                createPublicationReviewFixture();

        assertThat(
                AopUtils.isAopProxy(
                        problemPublicationFacade
                )
        ).isTrue();

        when(
                jsonMapper.writeValueAsString(
                        any(ProblemPublishedEvent.class)
                )
        ).thenReturn(
                """
                {
                  "eventType": "PROBLEM_PUBLISHED"
                }
                """
        );

        // when & then
        assertThatThrownBy(
                () ->
                        problemPublicationFacade
                                .approvePublication(
                                        problemId
                                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        FORCED_FAILURE_MESSAGE
                );

        /*
         * 예외 발생 직전 실제 Outbox가 PostgreSQL에 flush된 것을 확인합니다.
         */
        assertThat(
                failingAfterOutboxFlushPort
                        .wasOutboxFlushed()
        ).isTrue();

        RollbackResult rollbackResult =
                readRollbackResult(
                        problemId
                );

        /*
         * Facade 트랜잭션 내부에서는 PUBLISHED로 변경되었지만
         * rollback 후에는 기존 REVIEW_PENDING 상태여야 합니다.
         */
        assertThat(
                rollbackResult.problemStatus()
        ).isEqualTo(
                ProblemStatus.REVIEW_PENDING
        );

        /*
         * 발행 승인 과정의 currentVersionNo 증가도 rollback되어야 합니다.
         */
        assertThat(
                rollbackResult.currentVersionNo()
        ).isEqualTo(
                1
        );

        /*
         * 실제 DB에 flush된 ProblemVersion INSERT도 rollback되어야 합니다.
         */
        assertThat(
                rollbackResult.problemVersionCount()
        ).isZero();

        /*
         * 실제 DB에 flush된 ProblemEventOutbox INSERT도 rollback되어야 합니다.
         */
        assertThat(
                rollbackResult.outboxCount()
        ).isZero();

        /*
         * Facade 호출 전에 별도 트랜잭션으로 저장한 TestCase는 유지되어야 합니다.
         */
        assertThat(
                rollbackResult.testCaseCount()
        ).isEqualTo(
                2L
        );
    }


    /**
     * Facade 트랜잭션과 분리된 선행 트랜잭션에서
     * REVIEW_PENDING 문제와 승인된 테스트케이스를 저장합니다.
     */
    private UUID createPublicationReviewFixture() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        return transactionTemplate.execute(status -> {
            Problem problem =
                    createProblem();

            entityManager.persist(
                    problem
            );

            entityManager.flush();

            TestCase publicTestCase =
                    TestCase.createByAdmin(
                            problem.getId(),
                            "1 2",
                            "3",
                            true,
                            1
                    );

            TestCase hiddenTestCase =
                    TestCase.createByAdmin(
                            problem.getId(),
                            "10 20",
                            "30",
                            false,
                            2
                    );

            entityManager.persist(
                    publicTestCase
            );

            entityManager.persist(
                    hiddenTestCase
            );

            entityManager.flush();

            return problem.getId();
        });
    }


    /**
     * Facade 트랜잭션 종료 후 새로운 트랜잭션에서
     * 실제 PostgreSQL 상태를 다시 조회합니다.
     */
    private RollbackResult readRollbackResult(
            UUID problemId
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        return transactionTemplate.execute(status -> {
            entityManager.clear();

            Problem problem =
                    entityManager.find(
                            Problem.class,
                            problemId
                    );

            Long problemVersionCount =
                    entityManager.createQuery(
                                    """
                                    SELECT COUNT(problemVersion)
                                    FROM ProblemVersion problemVersion
                                    WHERE problemVersion.problemId = :problemId
                                    """,
                                    Long.class
                            )
                            .setParameter(
                                    "problemId",
                                    problemId
                            )
                            .getSingleResult();

            Long outboxCount =
                    entityManager.createQuery(
                                    """
                                    SELECT COUNT(outbox)
                                    FROM ProblemEventOutbox outbox
                                    WHERE outbox.aggregateId = :problemId
                                    """,
                                    Long.class
                            )
                            .setParameter(
                                    "problemId",
                                    problemId
                            )
                            .getSingleResult();

            Long testCaseCount =
                    entityManager.createQuery(
                                    """
                                    SELECT COUNT(testCase)
                                    FROM TestCase testCase
                                    WHERE testCase.problemId = :problemId
                                    """,
                                    Long.class
                            )
                            .setParameter(
                                    "problemId",
                                    problemId
                            )
                            .getSingleResult();

            return new RollbackResult(
                    problem.getProblemStatus(),
                    problem.getCurrentVersionNo(),
                    problemVersionCount,
                    outboxCount,
                    testCaseCount
            );
        });
    }


    /**
     * 통합 테스트용 REVIEW_PENDING 문제를 생성합니다.
     */
    private Problem createProblem() {
        return Problem.create(
                "두 수의 합",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수를 더한 값을 반환하세요.",
                "class Solution {}",
                RunningTimeLimit.SECOND_1,
                RunningMemoryLimit.MB_128,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.REVIEW_PENDING
        );
    }


    private record RollbackResult(
            ProblemStatus problemStatus,
            int currentVersionNo,
            long problemVersionCount,
            long outboxCount,
            long testCaseCount
    ) {
    }


    /**
     * 이번 트랜잭션 테스트에 필요한 Bean만 구성합니다.
     */
    @TestConfiguration(
            proxyBeanMethods = false
    )
    static class TestConfig {

        @Bean
        @Primary
        ProblemFinder problemFinder(
                EntityManager entityManager
        ) {
            return new ProblemFinder() {

                @Override
                public Problem getById(
                        UUID problemId
                ) {
                    Problem problem =
                            entityManager.find(
                                    Problem.class,
                                    problemId
                            );

                    return requireProblem(
                            problem
                    );
                }

                @Override
                public Problem lockById(
                        UUID problemId
                ) {
                    Problem problem =
                            entityManager.find(
                                    Problem.class,
                                    problemId,
                                    LockModeType.PESSIMISTIC_WRITE
                            );

                    return requireProblem(
                            problem
                    );
                }

                private Problem requireProblem(
                        Problem problem
                ) {
                    if (problem == null) {
                        throw new BusinessException(
                                ErrorCode.PROBLEM_NOT_FOUND
                        );
                    }

                    return problem;
                }
            };
        }


        @Bean
        @Primary
        TestCaseFinder testCaseFinder(
                EntityManager entityManager
        ) {
            return new TestCaseFinder() {

                @Override
                public TestCase getById(
                        UUID testCaseId
                ) {
                    TestCase testCase =
                            entityManager.find(
                                    TestCase.class,
                                    testCaseId
                            );

                    if (testCase == null) {
                        throw new BusinessException(
                                ErrorCode.TEST_CASE_NOT_FOUND
                        );
                    }

                    return testCase;
                }

                @Override
                public List<TestCase> findApprovedTestCases(
                        UUID problemId
                ) {
                    return entityManager.createQuery(
                                    """
                                    SELECT testCase
                                    FROM TestCase testCase
                                    WHERE testCase.problemId = :problemId
                                      AND testCase.testCaseStatus = :status
                                    ORDER BY testCase.isPublic DESC,
                                             testCase.testCaseOrder ASC,
                                             testCase.id ASC
                                    """,
                                    TestCase.class
                            )
                            .setParameter(
                                    "problemId",
                                    problemId
                            )
                            .setParameter(
                                    "status",
                                    TestCaseStatus.APPROVED
                            )
                            .getResultList();
                }
            };
        }


        /**
         * JSON 직렬화 자체가 아닌 transaction rollback 검증이 목적이므로
         * JsonMapper는 Mock으로 대체합니다.
         */
        @Bean
        @Primary
        JsonMapper jsonMapper() {
            return Mockito.mock(
                    JsonMapper.class
            );
        }


        /**
         * 실제 Outbox를 저장하고 flush한 직후 예외를 발생시킵니다.
         */
        @Bean
        @Primary
        FailingAfterOutboxFlushPort failingAfterOutboxFlushPort(
                ProblemPublishedEventAdapter problemPublishedEventAdapter,
                EntityManager entityManager
        ) {
            return new FailingAfterOutboxFlushPort(
                    problemPublishedEventAdapter,
                    entityManager
            );
        }
    }


    /**
     * 실제 Outbox INSERT까지 수행한 뒤
     * Facade 트랜잭션을 강제로 실패시키는 테스트 전용 Port입니다.
     */
    static class FailingAfterOutboxFlushPort
            implements ProblemPublishedEventPort {

        private final ProblemPublishedEventAdapter delegate;

        private final EntityManager entityManager;

        private boolean outboxFlushed;


        FailingAfterOutboxFlushPort(
                ProblemPublishedEventAdapter delegate,
                EntityManager entityManager
        ) {
            this.delegate = delegate;
            this.entityManager = entityManager;
        }


        @Override
        public void record(
                ProblemPublishedEventData eventData
        ) {
            delegate.record(
                    eventData
            );

            /*
             * Problem UPDATE, ProblemVersion INSERT,
             * ProblemEventOutbox INSERT를 실제 PostgreSQL에 전달합니다.
             */
            entityManager.flush();

            Long outboxCount =
                    entityManager.createQuery(
                                    """
                                    SELECT COUNT(outbox)
                                    FROM ProblemEventOutbox outbox
                                    WHERE outbox.aggregateId = :problemId
                                    """,
                                    Long.class
                            )
                            .setParameter(
                                    "problemId",
                                    eventData
                                            .problemVersion()
                                            .getProblemId()
                            )
                            .getSingleResult();

            this.outboxFlushed =
                    outboxCount == 1L;

            if (!this.outboxFlushed) {
                throw new AssertionError(
                        "ProblemEventOutbox was not flushed"
                );
            }

            throw new IllegalStateException(
                    FORCED_FAILURE_MESSAGE
            );
        }


        boolean wasOutboxFlushed() {
            return outboxFlushed;
        }
    }
}