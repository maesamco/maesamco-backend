package com.maesamco.content.integration;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.maesamco.content.application.command_service.ProblemProgressCommandService;
import com.maesamco.content.application.finder.ProblemProgressFinder;
import com.maesamco.content.application.finder_service.ProblemFinderService;
import com.maesamco.content.application.finder_service.ProblemProgressFinderService;
import com.maesamco.content.application.finder_service.ProblemVersionFinderService;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.config.QuerydslConfig;
import com.maesamco.content.infrastructure.messaging.consumer.SubmissionJudgedKafkaConsumer;
import com.maesamco.content.infrastructure.messaging.event.SubmissionJudgedEvent;
import com.maesamco.content.infrastructure.persistence.ProblemCommandRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.ProblemProgressRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.ProblemQueryRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.ProblemVersionRepositoryImpl;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.default_schema=content_schema"
        }
)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(
        FlywayAutoConfiguration.class
)
@Import({
        JpaAuditingConfig.class,
        QuerydslConfig.class,
        ProblemQueryRepositoryImpl.class,
        ProblemCommandRepositoryImpl.class,
        ProblemProgressRepositoryImpl.class,
        ProblemVersionRepositoryImpl.class,
        ProblemFinderService.class,
        ProblemProgressFinderService.class,
        ProblemVersionFinderService.class,
        ProblemProgressCommandService.class,
        SubmissionJudgedKafkaConsumer.class
})
@DisplayName("SubmissionJudged → ProblemProgress 통합 테스트")
class SubmissionJudgedProblemProgressIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    @Autowired
    private SubmissionJudgedKafkaConsumer submissionJudgedKafkaConsumer;

    @Autowired
    private ProblemProgressFinder problemProgressFinder;

    @Autowired
    private ProblemVersionRepository problemVersionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName(
            "SubmissionJudged 이벤트를 수신하면 ProblemProgress가 저장되고 "
                    + "더 최신 제출을 수신하면 기존 진행도가 갱신되어 조회된다"
    )
    void consume_createsAndUpdatesProblemProgress_thenCanBeQueried() {
        // given
        UUID userId = UUID.randomUUID();

        Instant firstJudgedAt =
                Instant.parse("2026-09-21T01:00:00Z");

        Instant secondJudgedAt =
                Instant.parse("2026-09-21T01:05:00Z");

        Problem problem =
                Problem.create(
                        "통합 테스트 문제",
                        ProgrammingLanguage.JAVA,
                        ProblemDifficulty.EASY,
                        ProblemType.CODE,
                        "통합 테스트 문제 설명",
                        "public class Main {}",
                        RunningTimeLimit.SECOND_1,
                        RunningMemoryLimit.MB_128,
                        TimerPolicy.APPLY60,
                        ProblemSource.HUMAN_AUTHORED,
                        ProblemStatus.PUBLISHED
                );

        entityManager.persist(problem);
        entityManager.flush();

        UUID problemId = problem.getId();

        ProblemVersion version1 =
                problemVersionRepository.save(
                        ProblemVersion.create(
                                problemId,
                                1,
                                JsonNodeFactory.instance
                                        .objectNode()
                                        .put("version", 1)
                        )
                );

        ProblemVersion version2 =
                problemVersionRepository.save(
                        ProblemVersion.create(
                                problemId,
                                2,
                                JsonNodeFactory.instance
                                        .objectNode()
                                        .put("version", 2)
                        )
                );

        problemVersionRepository.flush();

        UUID version1Id = version1.getId();
        UUID version2Id = version2.getId();

        entityManager.clear();

        SubmissionJudgedEvent firstEvent =
                new SubmissionJudgedEvent(
                        UUID.randomUUID(),
                        userId,
                        problemId,
                        version1Id,
                        1,
                        "COMPLETED",
                        "WRONG",
                        firstJudgedAt
                );

        // when
        submissionJudgedKafkaConsumer.consume(firstEvent);

        entityManager.flush();
        entityManager.clear();

        // then
        ProblemProgress created =
                problemProgressFinder
                        .getByUserIdAndProblemId(
                                userId,
                                problemId
                        )
                        .orElseThrow();

        UUID problemProgressId = created.getId();

        assertThat(created.getUserId())
                .isEqualTo(userId);

        assertThat(created.getProblemId())
                .isEqualTo(problemId);

        assertThat(created.getVersionNo())
                .isEqualTo(1);

        assertThat(created.getAttemptNo())
                .isEqualTo(1);

        assertThat(created.getProgressStatus())
                .isEqualTo(
                        ProblemProgressStatus.WRONG
                );

        assertThat(created.getCreatedAt())
                .isEqualTo(firstJudgedAt);

        assertThat(created.getSolvedAt())
                .isNull();

        List<ProblemProgress> createdProgresses =
                problemProgressFinder.getByUserId(
                        userId
                );

        assertThat(createdProgresses)
                .hasSize(1);

        assertThat(createdProgresses.get(0).getId())
                .isEqualTo(problemProgressId);

        // given
        SubmissionJudgedEvent secondEvent =
                new SubmissionJudgedEvent(
                        UUID.randomUUID(),
                        userId,
                        problemId,
                        version2Id,
                        2,
                        "COMPLETED",
                        "CORRECT",
                        secondJudgedAt
                );

        // when
        submissionJudgedKafkaConsumer.consume(secondEvent);

        entityManager.flush();
        entityManager.clear();

        // then
        ProblemProgress updated =
                problemProgressFinder
                        .getByUserIdAndProblemId(
                                userId,
                                problemId
                        )
                        .orElseThrow();

        assertThat(updated.getId())
                .isEqualTo(problemProgressId);

        assertThat(updated.getUserId())
                .isEqualTo(userId);

        assertThat(updated.getProblemId())
                .isEqualTo(problemId);

        assertThat(updated.getVersionNo())
                .isEqualTo(2);

        assertThat(updated.getAttemptNo())
                .isEqualTo(2);

        assertThat(updated.getProgressStatus())
                .isEqualTo(
                        ProblemProgressStatus.CORRECT
                );

        assertThat(updated.getCreatedAt())
                .isEqualTo(firstJudgedAt);

        assertThat(updated.getSolvedAt())
                .isEqualTo(secondJudgedAt);

        List<ProblemProgress> updatedProgresses =
                problemProgressFinder.getByUserId(
                        userId
                );

        assertThat(updatedProgresses)
                .hasSize(1);

        assertThat(updatedProgresses.get(0).getId())
                .isEqualTo(problemProgressId);
    }
}