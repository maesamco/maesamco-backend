package com.maesamco.content.problem.application.service;

import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import com.maesamco.content.problem.domain.entity.ProblemVersion;
import com.maesamco.content.problem.domain.entity.TestCase;
import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.problem.domain.enums.TimerPolicy;
import com.maesamco.content.problem.domain.repository.ProblemEventOutboxRepository;
import com.maesamco.content.problem.domain.repository.ProblemRepository;
import com.maesamco.content.problem.domain.repository.ProblemVersionRepository;
import com.maesamco.content.problem.domain.repository.TestCaseRepository;
import com.maesamco.content.problem.infrastructure.messaging.event.ProblemPublishedEvent;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 문제 발행 승인 트랜잭션의 실제 롤백 동작을
 * PostgreSQL 환경에서 검증합니다.
 *
 * <p>테스트 자체는 트랜잭션을 시작하지 않으며,
 * Spring 프록시를 통해 호출되는
 * ProblemPublicationService의 @Transactional만 사용합니다.</p>
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
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        JpaAuditingConfig.class,
        ProblemPublicationService.class,
        ProblemPublicationTransactionIntegrationTest.TestConfig.class
})
@EnableJpaRepositories(
        basePackageClasses = {
                ProblemVersionRepository.class,
                TestCaseRepository.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        ProblemRepository.class,
                        ProblemEventOutboxRepository.class
                }
        )
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
    private ProblemPublicationService problemPublicationService;

    @Autowired
    private ProblemVersionRepository problemVersionRepository;

    @Autowired
    private ProblemEventOutboxRepository problemEventOutboxRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName(
            "Outbox 저장이 실패하면 "
                    + "PUBLISHED 상태 변경과 ProblemVersion 저장이 모두 롤백된다"
    )
    void approvePublication_outboxFailure_rollsBackEntireTransaction()
            throws Exception {
        // given
        UUID problemId =
                createPublishedReviewFixture();

        assertThat(
                AopUtils.isAopProxy(
                        problemPublicationService
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

        /*
         * ProblemVersion.save() 이후 실제 PostgreSQL로 flush하여
         * Problem의 PUBLISHED UPDATE와 ProblemVersion INSERT가
         * DB에 전달된 상태를 만든 다음 강제로 실패시킵니다.
         *
         * 이 예외가 서비스 @Transactional 경계를 빠져나갈 때
         * 전체 트랜잭션이 rollback되어야 합니다.
         */
        when(
                problemEventOutboxRepository.save(
                        any(ProblemEventOutbox.class)
                )
        ).thenAnswer(invocation -> {
            problemVersionRepository.flush();

            throw new IllegalStateException(
                    FORCED_FAILURE_MESSAGE
            );
        });

        // when & then
        assertThatThrownBy(
                () ->
                        problemPublicationService
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

        verify(
                problemEventOutboxRepository
        ).save(
                any(ProblemEventOutbox.class)
        );

        RollbackResult rollbackResult =
                readRollbackResult(
                        problemId
                );

        /*
         * 서비스 트랜잭션에서 PUBLISHED로 변경되었지만
         * 최종 rollback 후에는 기존 REVIEW_PENDING 상태여야 합니다.
         */
        assertThat(
                rollbackResult.problemStatus()
        ).isEqualTo(
                ProblemStatus.REVIEW_PENDING
        );

        /*
         * 서비스 내부 flush 시 PostgreSQL에 INSERT가 전달되었더라도
         * 트랜잭션 rollback 후에는 ProblemVersion이 없어야 합니다.
         */
        assertThat(
                rollbackResult.problemVersionCount()
        ).isZero();

        /*
         * 서비스 트랜잭션 이전에 별도 트랜잭션으로 저장한
         * TestCase는 그대로 남아 있어야 합니다.
         */
        assertThat(
                rollbackResult.testCaseCount()
        ).isEqualTo(
                2L
        );
    }

    /**
     * 서비스 트랜잭션과 분리된 선행 트랜잭션에서
     * REVIEW_PENDING 문제와 테스트케이스를 저장합니다.
     */
    private UUID createPublishedReviewFixture() {
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
                    TestCase.create(
                            problem.getId(),
                            true,
                            "1 2",
                            "3",
                            1
                    );

            TestCase hiddenTestCase =
                    TestCase.create(
                            problem.getId(),
                            false,
                            "10 20",
                            "30",
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
     * 서비스 트랜잭션 종료 후 새로운 트랜잭션에서
     * 실제 DB 상태를 다시 조회합니다.
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
                    problemVersionCount,
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
                1,
                128,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.REVIEW_PENDING,
                1
        );
    }

    /**
     * 롤백 이후 DB 상태 확인 결과입니다.
     */
    private record RollbackResult(
            ProblemStatus problemStatus,
            long problemVersionCount,
            long testCaseCount
    ) {
    }

    /**
     * 전체 ProblemRepository를 활성화하지 않고
     * 이번 트랜잭션 테스트에 필요한 Bean만 구성합니다.
     */
    @TestConfiguration(
            proxyBeanMethods = false
    )
    static class TestConfig {

        /**
         * 서비스 트랜잭션에 참여하는 ProblemFinder입니다.
         *
         * <p>ProblemRepository의 기존 custom fragment 문제를
         * 이번 #109 테스트 범위와 분리하기 위해 EntityManager를
         * 사용합니다.</p>
         */
        @Bean
        @Primary
        ProblemFinder problemFinder(
                EntityManager entityManager
        ) {
            return problemId -> {
                Problem problem =
                        entityManager.find(
                                Problem.class,
                                problemId
                        );

                if (problem == null) {
                    throw new BusinessException(
                            ErrorCode.PROBLEM_NOT_FOUND
                    );
                }

                return problem;
            };
        }

        /**
         * 마지막 Outbox 저장 단계에서 강제 실패를 만들기 위한
         * 테스트 전용 Repository Mock입니다.
         */
        @Bean
        @Primary
        ProblemEventOutboxRepository problemEventOutboxRepository() {
            return Mockito.mock(
                    ProblemEventOutboxRepository.class
            );
        }

        /**
         * 이번 테스트의 목적은 JSON 자체가 아니라
         * 트랜잭션 rollback이므로 JsonMapper는 Mock으로 대체합니다.
         */
        @Bean
        @Primary
        JsonMapper jsonMapper() {
            return Mockito.mock(
                    JsonMapper.class
            );
        }
    }
}
