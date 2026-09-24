package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.TestCaseStatus;
import com.maesamco.content.domain.entity.problem.*;
import com.maesamco.content.domain.repository.TestCaseRepository;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TestCaseRepositoryImpl의 테스트케이스 조회 규칙을
 * 실제 PostgreSQL 환경에서 검증합니다.
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
        TestCaseRepositoryImpl.class
})
@EnableJpaRepositories(
        basePackageClasses = {
                SpringDataTestCaseRepository.class
        }
)
class TestCaseRepositoryImplTest {

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
            "문제 발행용 테스트케이스 조회 시 승인되고 삭제되지 않은 항목만 정해진 순서로 반환한다"
    )
    void findAllForPublication_returnsOnlyApprovedActiveTestCasesInOrder() {
        // given
        Problem problem = createProblem();

        entityManager.persist(problem);
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
                .extracting(TestCase::getIsPublic)
                .containsExactly(
                        true,
                        false
                );

        assertThat(found)
                .allMatch(
                        testCase ->
                                testCase.getTestCaseStatus()
                                        == TestCaseStatus.APPROVED
                );

        assertThat(found)
                .allMatch(
                        testCase ->
                                testCase.getDeletedAt() == null
                );
    }

    @Test
    @DisplayName(
            "승인된 테스트케이스가 없으면 빈 목록을 반환한다"
    )
    void findAllForPublication_noApprovedTestCases_returnsEmptyList() {
        // given
        Problem problem = createProblem();

        entityManager.persist(problem);
        entityManager.flush();

        TestCase pending =
                TestCase.createByUser(
                        problem.getId(),
                        "1 1",
                        "2",
                        true,
                        1
                );

        testCaseRepository.save(pending);
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
                .isEmpty();
    }

    @Test
    @DisplayName(
            "다른 문제의 테스트케이스는 조회 결과에 포함하지 않는다"
    )
    void findAllForPublication_excludesOtherProblemTestCases() {
        // given
        Problem targetProblem =
                createProblem();

        Problem otherProblem =
                createProblem();

        entityManager.persist(targetProblem);
        entityManager.persist(otherProblem);
        entityManager.flush();

        TestCase targetTestCase =
                TestCase.createByAdmin(
                        targetProblem.getId(),
                        "1 2",
                        "3",
                        true,
                        1
                );

        TestCase otherTestCase =
                TestCase.createByAdmin(
                        otherProblem.getId(),
                        "10 20",
                        "30",
                        true,
                        1
                );

        testCaseRepository.saveAll(
                List.of(
                        targetTestCase,
                        otherTestCase
                )
        );

        testCaseRepository.flush();
        entityManager.clear();

        // when
        List<TestCase> found =
                testCaseRepository
                        .findAllByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
                                targetProblem.getId(),
                                TestCaseStatus.APPROVED
                        );

        // then
        assertThat(found)
                .hasSize(1);

        assertThat(found.get(0).getProblemId())
                .isEqualTo(targetProblem.getId());

        assertThat(found.get(0).getInput())
                .isEqualTo("1 2");
    }

    @Test
    @DisplayName(
            "공개 테스트케이스 페이징 조회는 승인된 공개 항목만 testCaseOrder, id 순으로 반환하고 페이지 정보를 PageResult로 전달한다"
    )
    void searchTestCases_returnsApprovedPublicInOrderWithPageMetadata() {
        // given
        Problem problem = createProblem();
        Problem otherProblem = createProblem();
        entityManager.persist(problem);
        entityManager.persist(otherProblem);
        entityManager.flush();

        // order가 같은 두 항목은 id로 순서가 결정된다.
        TestCase first = TestCase.createByAdmin(problem.getId(), "1", "1", true, 1);
        TestCase tieA = TestCase.createByAdmin(problem.getId(), "2", "2", true, 2);
        TestCase tieB = TestCase.createByAdmin(problem.getId(), "3", "3", true, 2);
        TestCase third = TestCase.createByAdmin(problem.getId(), "4", "4", true, 3);
        TestCase hidden = TestCase.createByAdmin(problem.getId(), "5", "5", false, 1);
        TestCase pending = TestCase.createByUser(problem.getId(), "6", "6", true, 1);
        TestCase otherProblemCase = TestCase.createByAdmin(otherProblem.getId(), "7", "7", true, 1);
        TestCase deleted = TestCase.createByAdmin(problem.getId(), "8", "8", true, 4);
        deleted.softDelete(UUID.randomUUID());

        testCaseRepository.saveAll(List.of(first, tieA, tieB, third, hidden, pending, otherProblemCase, deleted));
        testCaseRepository.flush();
        entityManager.clear();

        List<TestCase> tied = List.of(tieA, tieB).stream()
                .sorted(Comparator.comparing(tc -> tc.getId().toString()))
                .toList();

        // when
        PageResult<TestCase> firstPage = testCaseRepository.searchTestCases(problem.getId(), true, PageQuery.of(0, 2));
        PageResult<TestCase> secondPage = testCaseRepository.searchTestCases(problem.getId(), true, PageQuery.of(1, 2));

        // then
        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(firstPage.content()).extracting(TestCase::getId)
                .containsExactly(first.getId(), tied.get(0).getId());

        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.content()).extracting(TestCase::getId)
                .containsExactly(tied.get(1).getId(), third.getId());
    }

    @Test
    @DisplayName(
            "전체 테스트케이스 페이징 조회는 승인된 항목을 공개 우선, testCaseOrder, id 순으로 반환하고 페이지 정보를 PageResult로 전달한다"
    )
    void searchTestCasesAll_returnsApprovedPublicFirstInOrderWithPageMetadata() {
        // given
        Problem problem = createProblem();
        entityManager.persist(problem);
        entityManager.flush();

        TestCase publicFirst = TestCase.createByAdmin(problem.getId(), "1", "1", true, 1);
        TestCase publicSecond = TestCase.createByAdmin(problem.getId(), "2", "2", true, 2);
        TestCase hiddenTieA = TestCase.createByAdmin(problem.getId(), "3", "3", false, 1);
        TestCase hiddenTieB = TestCase.createByAdmin(problem.getId(), "4", "4", false, 1);
        TestCase pending = TestCase.createByUser(problem.getId(), "5", "5", true, 1);
        TestCase deleted = TestCase.createByAdmin(problem.getId(), "6", "6", false, 2);
        deleted.softDelete(UUID.randomUUID());

        testCaseRepository.saveAll(List.of(publicFirst, publicSecond, hiddenTieA, hiddenTieB, pending, deleted));
        testCaseRepository.flush();
        entityManager.clear();

        List<TestCase> hiddenTied = List.of(hiddenTieA, hiddenTieB).stream()
                .sorted(Comparator.comparing(tc -> tc.getId().toString()))
                .toList();

        // when
        PageResult<TestCase> firstPage = testCaseRepository.searchTestCasesAll(problem.getId(), PageQuery.of(0, 3));
        PageResult<TestCase> secondPage = testCaseRepository.searchTestCasesAll(problem.getId(), PageQuery.of(1, 3));

        // then
        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.size()).isEqualTo(3);
        assertThat(firstPage.content()).extracting(TestCase::getId)
                .containsExactly(publicFirst.getId(), publicSecond.getId(), hiddenTied.get(0).getId());

        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.content()).extracting(TestCase::getId)
                .containsExactly(hiddenTied.get(1).getId());
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