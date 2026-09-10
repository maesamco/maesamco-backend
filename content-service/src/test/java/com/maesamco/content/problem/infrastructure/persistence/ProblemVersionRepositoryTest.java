package com.maesamco.content.problem.infrastructure.persistence;

import com.maesamco.content.global.common.BaseEntity;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.config.QuerydslConfig;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.ProblemVersion;
import com.maesamco.content.problem.domain.enums.*;
import com.maesamco.content.problem.domain.repository.ProblemRepository;
import com.maesamco.content.problem.domain.repository.ProblemVersionRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProblemVersion JPA 매핑과 JSONB 저장을
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
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        JpaAuditingConfig.class,
        QuerydslConfig.class
})
@EnableJpaRepositories(
        basePackageClasses = ProblemVersionRepository.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = ProblemRepository.class
        )
)
class ProblemVersionRepositoryTest {

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
    private ProblemVersionRepository problemVersionRepository;

    @Test
    @DisplayName(
            "ProblemVersion을 저장하면 "
                    + "발행 스냅샷이 PostgreSQL JSONB로 저장되고 복원된다"
    )
    void saveAndFind_restoresJsonbSnapshot() {
        // given
        Problem problem = createProblem();

        entityManager.persist(problem);
        entityManager.flush();

        UUID firstTestCaseId = UUID.randomUUID();
        UUID secondTestCaseId = UUID.randomUUID();

        List<ProblemVersion.TestCaseItem> testCases =
                List.of(
                        new ProblemVersion.TestCaseItem(
                                firstTestCaseId,
                                true,
                                "1 2",
                                "3",
                                1
                        ),
                        new ProblemVersion.TestCaseItem(
                                secondTestCaseId,
                                false,
                                "10 20",
                                "30",
                                2
                        )
                );

        Instant publishedAt = Instant.parse(
                "2026-09-08T00:00:00Z"
        );

        ProblemVersion problemVersion =
                ProblemVersion.createPublished(
                        problem.getId(),
                        problem,
                        testCases,
                        publishedAt
                );

        // when
        ProblemVersion saved =
                problemVersionRepository.saveAndFlush(
                        problemVersion
                );

        UUID problemVersionId = saved.getId();

        entityManager.clear();

        ProblemVersion found =
                problemVersionRepository.findById(
                        problemVersionId
                ).orElseThrow();

        // then
        assertThat(found.getId())
                .isEqualTo(problemVersionId);

        assertThat(found.getProblemId())
                .isEqualTo(problem.getId());

        assertThat(found.getVersionNo())
                .isEqualTo(1);

        assertThat(found.getPublishedAt())
                .isEqualTo(publishedAt);

        UUID createdBy = (UUID) entityManager
                .createNativeQuery(
                        """
                        SELECT created_by
                        FROM content_schema.p_problem_versions
                        WHERE id = ?1
                        """
                )
                .setParameter(
                        1,
                        problemVersionId
                )
                .getSingleResult();

        assertThat(createdBy)
                .isEqualTo(
                        BaseEntity.SYSTEM_ACTOR_ID
                );

        ProblemVersion.ProblemVersionSnapshot snapshot =
                found.getContentSnapshot();

        assertThat(snapshot.title())
                .isEqualTo("두 수의 합");

        assertThat(snapshot.language())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(snapshot.difficulty())
                .isEqualTo(ProblemDifficulty.EASY);

        assertThat(snapshot.type())
                .isEqualTo(ProblemType.CODE);

        assertThat(snapshot.description())
                .isEqualTo(
                        "두 정수를 더한 값을 반환하세요."
                );

        assertThat(snapshot.starterCode())
                .isEqualTo("class Solution {}");

        assertThat(snapshot.runningTimeLimit())
                .isEqualTo(1);

        assertThat(snapshot.runningMemoryLimit())
                .isEqualTo(128);

        assertThat(snapshot.timerPolicy())
                .isEqualTo(TimerPolicy.APPLY60);

        assertThat(snapshot.source())
                .isEqualTo(
                        ProblemSource.HUMAN_AUTHORED
                );

        assertThat(snapshot.testCases())
                .containsExactlyElementsOf(testCases);

        String columnType = (String) entityManager
                .createNativeQuery(
                        """
                        SELECT pg_typeof(problem_snapshot)::text
                        FROM content_schema.p_problem_versions
                        WHERE id = :problemVersionId
                        """
                )
                .setParameter("problemVersionId", problemVersionId)
                .getSingleResult();

        assertThat(columnType)
                .isEqualTo("jsonb");
    }

    /**
     * Repository 통합 테스트용 문제를 생성합니다.
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
}
