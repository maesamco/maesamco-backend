package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import com.maesamco.content.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.default_schema=content_schema"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        JpaAuditingConfig.class,
        ProblemProgressRepositoryImpl.class
})
class ProblemProgressRepositoryImplTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    @Autowired
    private ProblemProgressRepository problemProgressRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("ProblemProgress를 저장하면 userId와 problemId로 조회할 수 있다")
    void saveAndFindByUserIdAndProblemId_success() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = createProblem();
        Instant judgedAt = Instant.parse("2026-09-23T01:00:00Z");

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.WRONG,
                judgedAt
        );

        // when
        problemProgressRepository.save(progress);
        entityManager.flush();
        entityManager.clear();

        Optional<ProblemProgress> found =
                problemProgressRepository.findByUserIdAndProblemId(
                        userId,
                        problemId
                );

        // then
        assertThat(found).isPresent();

        ProblemProgress savedProgress = found.orElseThrow();

        assertThat(savedProgress.getUserId()).isEqualTo(userId);
        assertThat(savedProgress.getProblemId()).isEqualTo(problemId);
        assertThat(savedProgress.getVersionNo()).isEqualTo(1);
        assertThat(savedProgress.getAttemptNo()).isEqualTo(1);
        assertThat(savedProgress.getProgressStatus()).isEqualTo(ProblemProgressStatus.WRONG);
        assertThat(savedProgress.getCreatedAt()).isEqualTo(judgedAt);
        assertThat(savedProgress.getSolvedAt()).isNull();
    }

    @Test
    @DisplayName("userId와 problemId가 일치하지 않으면 ProblemProgress를 조회하지 않는다")
    void findByUserIdAndProblemId_notMatched_returnsEmpty() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = createProblem();

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.WRONG,
                Instant.parse("2026-09-23T01:00:00Z")
        );

        problemProgressRepository.save(progress);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<ProblemProgress> found =
                problemProgressRepository.findByUserIdAndProblemId(
                        UUID.randomUUID(),
                        problemId
                );

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("기존 ProblemProgress를 변경하면 dirty checking으로 최신 상태가 DB에 반영된다")
    void update_existingProgress_reflectsLatestState() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = createProblem();

        Instant firstJudgedAt = Instant.parse("2026-09-23T01:00:00Z");
        Instant secondJudgedAt = Instant.parse("2026-09-23T02:00:00Z");

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.WRONG,
                firstJudgedAt
        );

        problemProgressRepository.save(progress);
        entityManager.flush();
        entityManager.clear();

        ProblemProgress found =
                problemProgressRepository
                        .findByUserIdAndProblemId(
                                userId,
                                problemId
                        )
                        .orElseThrow();

        // when
        found.changeVersionNo(2);
        found.changeAttemptNo(2);
        found.changeStatusCorrect();
        found.changeSolvedAt(
                ProblemProgressStatus.CORRECT,
                secondJudgedAt
        );

        entityManager.flush();
        entityManager.clear();

        ProblemProgress updated =
                problemProgressRepository
                        .findByUserIdAndProblemId(
                                userId,
                                problemId
                        )
                        .orElseThrow();

        // then
        assertThat(updated.getVersionNo()).isEqualTo(2);
        assertThat(updated.getAttemptNo()).isEqualTo(2);
        assertThat(updated.getProgressStatus()).isEqualTo(ProblemProgressStatus.CORRECT);
        assertThat(updated.getCreatedAt()).isEqualTo(firstJudgedAt);
        assertThat(updated.getSolvedAt()).isEqualTo(secondJudgedAt);
    }

    @Test
    @DisplayName("동일한 userId와 problemId로 두 개의 ProblemProgress를 저장할 수 없다")
    void save_duplicateUserIdAndProblemId_throwsException() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = createProblem();

        ProblemProgress firstProgress = ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.WRONG,
                Instant.parse("2026-09-23T01:00:00Z")
        );

        ProblemProgress duplicatedProgress = ProblemProgress.create(
                userId,
                problemId,
                2,
                2,
                ProblemProgressStatus.CORRECT,
                Instant.parse("2026-09-23T02:00:00Z")
        );

        problemProgressRepository.save(firstProgress);
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> {
            problemProgressRepository.save(duplicatedProgress);
            entityManager.flush();
        })
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uq_p_problem_progress_user_problem");
    }

    @Test
    @DisplayName("Progress 상태가 갱신되면 동일 userId와 problemId 조회 시 최신 상태를 반환한다")
    void findByUserIdAndProblemId_afterUpdate_returnsLatestState() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = createProblem();

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.WRONG,
                Instant.parse("2026-09-23T01:00:00Z")
        );

        problemProgressRepository.save(progress);
        entityManager.flush();
        entityManager.clear();

        ProblemProgress managedProgress =
                problemProgressRepository
                        .findByUserIdAndProblemId(
                                userId,
                                problemId
                        )
                        .orElseThrow();

        managedProgress.changeVersionNo(3);
        managedProgress.changeAttemptNo(4);
        managedProgress.changeStatusCorrect();
        managedProgress.changeSolvedAt(
                ProblemProgressStatus.CORRECT,
                Instant.parse("2026-09-23T03:00:00Z")
        );

        entityManager.flush();
        entityManager.clear();

        // when
        ProblemProgress latest =
                problemProgressRepository
                        .findByUserIdAndProblemId(
                                userId,
                                problemId
                        )
                        .orElseThrow();

        // then
        assertThat(latest.getUserId()).isEqualTo(userId);
        assertThat(latest.getProblemId()).isEqualTo(problemId);
        assertThat(latest.getVersionNo()).isEqualTo(3);
        assertThat(latest.getAttemptNo()).isEqualTo(4);
        assertThat(latest.getProgressStatus()).isEqualTo(ProblemProgressStatus.CORRECT);
        assertThat(latest.getSolvedAt()).isEqualTo(
                Instant.parse("2026-09-23T03:00:00Z")
        );
    }

    private UUID createProblem() {
        Problem problem = Problem.create(
                "ProblemProgress Repository 테스트 문제",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "ProblemProgress Repository 테스트용 문제입니다.",
                "public class Main {}",
                RunningTimeLimit.SECOND_1,
                RunningMemoryLimit.MB_128,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.PUBLISHED
        );

        entityManager.persist(problem);
        entityManager.flush();

        return problem.getId();
    }
}