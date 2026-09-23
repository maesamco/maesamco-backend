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
import com.maesamco.content.infrastructure.messaging.consumer.SubmissionJudgedKafkaConsumer;
import com.maesamco.content.infrastructure.messaging.event.SubmissionJudgedEvent;
import com.maesamco.content.infrastructure.persistence.ProblemProgressRepositoryImpl;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        JpaAuditingConfig.class,
        ProblemProgressRepositoryImpl.class,
        ProblemVersionRepositoryImpl.class,
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
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private SubmissionJudgedKafkaConsumer submissionJudgedKafkaConsumer;

    @Autowired
    private ProblemProgressFinder problemProgressFinder;

    @Autowired
    private ProblemVersionRepository problemVersionRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private ProblemFinderService problemFinderService;

    @Test
    @DisplayName("최초 WRONG 이벤트로 row를 생성하고 다음 CORRECT attempt로 같은 row를 갱신한다")
    void consume_firstWrongThenNextCorrect_updatesSameRow() {
        // given
        UUID userId = UUID.randomUUID();

        Instant firstJudgedAt = Instant.parse("2026-09-21T01:00:00Z");
        Instant secondJudgedAt = Instant.parse("2026-09-21T01:05:00Z");

        Problem problem = createProblem();
        UUID problemId = problem.getId();

        ProblemVersion version1 = createProblemVersion(problemId, 1);
        ProblemVersion version2 = createProblemVersion(problemId, 2);

        UUID version1Id = version1.getId();
        UUID version2Id = version2.getId();

        when(problemFinderService.getById(problemId)).thenReturn(problem);

        SubmissionJudgedEvent firstEvent = createEvent(
                userId,
                problemId,
                version1Id,
                1,
                "WRONG",
                firstJudgedAt
        );

        // when
        submissionJudgedKafkaConsumer.consume(firstEvent);

        entityManager.flush();
        entityManager.clear();

        // then
        ProblemProgress created = problemProgressFinder
                .getByUserIdAndProblemId(userId, problemId)
                .orElseThrow();

        UUID problemProgressId = created.getId();

        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getProblemId()).isEqualTo(problemId);
        assertThat(created.getVersionNo()).isEqualTo(1);
        assertThat(created.getAttemptNo()).isEqualTo(1);
        assertThat(created.getProgressStatus()).isEqualTo(ProblemProgressStatus.WRONG);
        assertThat(created.getCreatedAt()).isEqualTo(firstJudgedAt);
        assertThat(created.getSolvedAt()).isNull();
        assertThat(problemProgressFinder.getByUserId(userId)).hasSize(1);

        // given
        SubmissionJudgedEvent secondEvent = createEvent(
                userId,
                problemId,
                version2Id,
                2,
                "CORRECT",
                secondJudgedAt
        );

        // when
        submissionJudgedKafkaConsumer.consume(secondEvent);

        entityManager.flush();
        entityManager.clear();

        // then
        ProblemProgress updated = problemProgressFinder
                .getByUserIdAndProblemId(userId, problemId)
                .orElseThrow();

        assertThat(updated.getId()).isEqualTo(problemProgressId);
        assertThat(updated.getUserId()).isEqualTo(userId);
        assertThat(updated.getProblemId()).isEqualTo(problemId);
        assertThat(updated.getVersionNo()).isEqualTo(2);
        assertThat(updated.getAttemptNo()).isEqualTo(2);
        assertThat(updated.getProgressStatus()).isEqualTo(ProblemProgressStatus.CORRECT);
        assertThat(updated.getCreatedAt()).isEqualTo(firstJudgedAt);
        assertThat(updated.getSolvedAt()).isEqualTo(secondJudgedAt);

        List<ProblemProgress> progresses = problemProgressFinder.getByUserId(userId);

        assertThat(progresses).hasSize(1);
        assertThat(progresses.get(0).getId()).isEqualTo(problemProgressId);
    }

    @Test
    @DisplayName("동일한 SubmissionJudged 이벤트가 다시 도착해도 row와 상태가 중복 생성되거나 변경되지 않는다")
    void consume_duplicateEvent_isIdempotent() {
        // given
        UUID userId = UUID.randomUUID();
        Instant judgedAt = Instant.parse("2026-09-21T02:00:00Z");

        Problem problem = createProblem();
        UUID problemId = problem.getId();

        ProblemVersion version = createProblemVersion(problemId, 1);

        when(problemFinderService.getById(problemId)).thenReturn(problem);

        SubmissionJudgedEvent event = createEvent(
                userId,
                problemId,
                version.getId(),
                1,
                "CORRECT",
                judgedAt
        );

        submissionJudgedKafkaConsumer.consume(event);

        entityManager.flush();
        entityManager.clear();

        ProblemProgress first = problemProgressFinder
                .getByUserIdAndProblemId(userId, problemId)
                .orElseThrow();

        UUID problemProgressId = first.getId();

        // when
        submissionJudgedKafkaConsumer.consume(event);

        entityManager.flush();
        entityManager.clear();

        // then
        ProblemProgress duplicated = problemProgressFinder
                .getByUserIdAndProblemId(userId, problemId)
                .orElseThrow();

        assertThat(duplicated.getId()).isEqualTo(problemProgressId);
        assertThat(duplicated.getVersionNo()).isEqualTo(1);
        assertThat(duplicated.getAttemptNo()).isEqualTo(1);
        assertThat(duplicated.getProgressStatus()).isEqualTo(ProblemProgressStatus.CORRECT);
        assertThat(duplicated.getCreatedAt()).isEqualTo(judgedAt);
        assertThat(duplicated.getSolvedAt()).isEqualTo(judgedAt);

        List<ProblemProgress> progresses = problemProgressFinder.getByUserId(userId);

        assertThat(progresses).hasSize(1);
        assertThat(progresses.get(0).getId()).isEqualTo(problemProgressId);
    }

    @Test
    @DisplayName("더 오래된 attempt가 늦게 도착해도 최신 상태는 덮어쓰지 않고 더 이른 createdAt만 반영한다")
    void consume_staleEvent_doesNotOverwriteLatestState() {
        // given
        UUID userId = UUID.randomUUID();

        Instant latestJudgedAt = Instant.parse("2026-09-21T02:00:00Z");
        Instant staleJudgedAt = Instant.parse("2026-09-21T01:00:00Z");

        Problem problem = createProblem();
        UUID problemId = problem.getId();

        ProblemVersion version1 = createProblemVersion(problemId, 1);
        ProblemVersion version2 = createProblemVersion(problemId, 2);

        when(problemFinderService.getById(problemId)).thenReturn(problem);

        SubmissionJudgedEvent latestEvent = createEvent(
                userId,
                problemId,
                version2.getId(),
                2,
                "CORRECT",
                latestJudgedAt
        );

        submissionJudgedKafkaConsumer.consume(latestEvent);

        entityManager.flush();
        entityManager.clear();

        ProblemProgress latest = problemProgressFinder
                .getByUserIdAndProblemId(userId, problemId)
                .orElseThrow();

        UUID problemProgressId = latest.getId();

        SubmissionJudgedEvent staleEvent = createEvent(
                userId,
                problemId,
                version1.getId(),
                1,
                "WRONG",
                staleJudgedAt
        );

        // when
        submissionJudgedKafkaConsumer.consume(staleEvent);

        entityManager.flush();
        entityManager.clear();

        // then
        ProblemProgress result = problemProgressFinder
                .getByUserIdAndProblemId(userId, problemId)
                .orElseThrow();

        assertThat(result.getId()).isEqualTo(problemProgressId);
        assertThat(result.getVersionNo()).isEqualTo(2);
        assertThat(result.getAttemptNo()).isEqualTo(2);
        assertThat(result.getProgressStatus()).isEqualTo(ProblemProgressStatus.CORRECT);
        assertThat(result.getSolvedAt()).isEqualTo(latestJudgedAt);
        assertThat(result.getCreatedAt()).isEqualTo(staleJudgedAt);
        assertThat(problemProgressFinder.getByUserId(userId)).hasSize(1);
    }

    private Problem createProblem() {
        Problem problem = Problem.create(
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

        return problem;
    }

    private ProblemVersion createProblemVersion(UUID problemId, int versionNo) {
        ProblemVersion version = problemVersionRepository.save(
                ProblemVersion.create(
                        problemId,
                        versionNo,
                        JsonNodeFactory.instance.objectNode().put("version", versionNo)
                )
        );

        problemVersionRepository.flush();

        return version;
    }

    private SubmissionJudgedEvent createEvent(
            UUID userId,
            UUID problemId,
            UUID problemVersionId,
            int attemptNo,
            String result,
            Instant judgedAt
    ) {
        return new SubmissionJudgedEvent(
                UUID.randomUUID(),
                userId,
                problemId,
                problemVersionId,
                attemptNo,
                "COMPLETED",
                result,
                judgedAt
        );
    }
}