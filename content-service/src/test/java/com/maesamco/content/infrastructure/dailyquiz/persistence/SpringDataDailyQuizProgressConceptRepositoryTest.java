package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import com.maesamco.content.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class SpringDataDailyQuizProgressConceptRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SpringDataDailyQuizProgressConceptRepository repository;

    @Test
    void 개념_태그만_조회하고_정답은_퀴즈_시작_전_풀이만_포함한다() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-09-22T15:00:00Z");

        persistProgress(userId, "오답 개념", TagAttribute.CONCEPT, ProblemProgressStatus.WRONG,
                cutoff.minusSeconds(60));
        persistProgress(userId, "제외할 분류", TagAttribute.ETC, ProblemProgressStatus.WRONG,
                cutoff.minusSeconds(60));
        persistProgress(userId, "이전 정답", TagAttribute.CONCEPT, ProblemProgressStatus.CORRECT,
                cutoff.minusSeconds(1));
        persistProgress(userId, "경계 정답", TagAttribute.CONCEPT, ProblemProgressStatus.CORRECT,
                cutoff);
        persistProgress(otherUserId, "다른 사용자", TagAttribute.CONCEPT, ProblemProgressStatus.WRONG,
                cutoff.minusSeconds(60));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.existsByUserId(userId)).isTrue();
        assertThat(repository.findConceptTagsByStatus(userId, ProblemProgressStatus.WRONG,
                TagAttribute.CONCEPT)).containsExactly("오답 개념");
        assertThat(repository.findConceptTagsByStatusAndSolvedBefore(userId,
                ProblemProgressStatus.CORRECT, cutoff, TagAttribute.CONCEPT))
                .containsExactly("이전 정답");
    }

    private void persistProgress(UUID userId, String tagName, TagAttribute attribute,
                                 ProblemProgressStatus status, Instant judgedAt) {
        UUID problemId = UUID.randomUUID();
        Tag tag = Tag.create(tagName, attribute);
        entityManager.persist(tag);
        entityManager.persist(ProblemTag.create(problemId, tag.getId()));
        entityManager.persist(ProblemProgress.create(userId, problemId, 1, 1, status, judgedAt));
    }
}
