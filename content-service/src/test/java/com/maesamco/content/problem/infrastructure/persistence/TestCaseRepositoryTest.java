package com.maesamco.content.problem.infrastructure.persistence;

import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.config.QuerydslConfig;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.problem.domain.enums.RunningMemoryLimit;
import com.maesamco.content.problem.domain.enums.RunningTimeLimit;
import com.maesamco.content.problem.domain.enums.TimerPolicy;
import com.maesamco.content.problem.domain.repository.ProblemRepository;
import com.maesamco.content.testcase.domain.entity.TestCase;
import com.maesamco.content.testcase.domain.enums.TestCaseStatus;
import com.maesamco.content.testcase.domain.repository.TestCaseRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문제 발행 시 사용할 테스트케이스 조회 규칙을
 * 실제 PostgreSQL에서 검증합니다.
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
        QuerydslConfig.class
})
@EnableJpaRepositories(
        basePackageClasses = TestCaseRepository.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = ProblemRepository.class
        )
)
class TestCaseRepositoryTest {

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
    private TestCaseRepository testCaseRepository;

    @Test
    @DisplayName(
            "문제 발행용 테스트케이스 조회 시 "
                    + "승인되고 삭제되지 않은 항목만 정해진 순서로 반환한다"
    )
    void findAllForPublication_returnsOnlyApprovedActiveTestCasesInOrder() {
        // given
        Problem problem =
                createProblem();

        entityManager.persist(
                problem
        );

        entityManager.flush();

        TestCase hiddenSecond =
                TestCase.createByAdmin(
                        problem.getId(),
                        "10 20",
                        "30",
                        false,
                        2
                );

        TestCase publicFirst =
                TestCase.createByAdmin(
                        problem.getId(),
                        "1 2",
                        "3",
                        true,
                        1
                );

        TestCase deleted =
                TestCase.createByAdmin(
                        problem.getId(),
                        "100 200",
                        "300",
                        false,
                        3
                );

        /*
         * 사용자 생성 테스트케이스는 PENDING 상태이므로
         * 발행 대상에서 제외되어야 합니다.
         */
        TestCase pending =
                TestCase.createByUser(
                        problem.getId(),
                        "5 5",
                        "10",
                        true,
                        2
                );

        testCaseRepository.saveAll(
                List.of(
                        hiddenSecond,
                        publicFirst,
                        deleted,
                        pending
                )
        );

        testCaseRepository.flush();

        deleted.softDelete(
                UUID.randomUUID()
        );

        testCaseRepository.flush();

        entityManager.clear();

        // when
        List<TestCase> found =
                testCaseRepository
                        .findAllByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
                                problem.getId(),
                                TestCaseStatus.APPROVED
                        );

        // then
        assertThat(found)
                .hasSize(2);

        assertThat(found)
                .extracting(TestCase::getInput)
                .containsExactly(
                        "1 2",
                        "10 20"
                );

        assertThat(found)
                .extracting(TestCase::getTestCaseOrder)
                .containsExactly(
                        1,
                        2
                );

        assertThat(found)
                .allMatch(
                        testCase ->
                                testCase.getTestCaseStatus()
                                        == TestCaseStatus.APPROVED
                );
    }

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
}
