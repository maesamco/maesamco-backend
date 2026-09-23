package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 이슈 #307 — {@code findTagsByProblemId}에 {@code PageableFactory}가 만든 기본 정렬(createdAt desc)이
 * 담긴 {@code Pageable}을 넘기면, Spring Data가 그 동적 Sort를 쿼리의 FROM 절 첫 번째 별칭
 * (comma-FROM에서는 {@code problemTag})에 붙인다 — {@code ProblemTag}는 {@code BaseEntity}를
 * 상속하지 않아 {@code createdAt}이 없으므로 500으로 이어진다. 실제 운영 서버에서
 * {@code GET /contents/problems/{problemId}/tags} 호출 시 100% 재현되던 버그를 실제 PostgreSQL로 재현한다.
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
        JpaAuditingConfig.class
})
@EnableJpaRepositories(
        basePackageClasses = {
                SpringDataProblemTagRepository.class
        }
)
class SpringDataProblemTagRepositorySortIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    static {
        postgres.start();
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SpringDataProblemTagRepository springDataProblemTagRepository;

    @Test
    @DisplayName("컨트롤러가 실제로 넘기는 기본 정렬(createdAt desc) Pageable로 조회해도 500 없이 정상 조회된다")
    void findTagsByProblemId_withDefaultCreatedAtSortPageable_doesNotFail() {
        // given — ProblemTagController.getProblemTags()가 PageableFactory.of(page, size, null, null)로
        // 실제로 만들어내는 것과 동일한 Pageable(정렬 프로퍼티 미지정 시 createdAt desc 기본값).
        Problem problem = createProblem();
        entityManager.persist(problem);
        UUID problemId = problem.getId();

        Tag tag1 = Tag.create("배열", TagAttribute.DATA_STRUCTURE);
        Tag tag2 = Tag.create("반복문", TagAttribute.CONCEPT);
        entityManager.persist(tag1);
        entityManager.persist(tag2);

        entityManager.persist(ProblemTag.create(problemId, tag1.getId()));
        entityManager.persist(ProblemTag.create(problemId, tag2.getId()));
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

        // when / then
        assertThatCode(() -> springDataProblemTagRepository.findTagsByProblemId(problemId, pageable))
                .doesNotThrowAnyException();

        Page<Tag> result = springDataProblemTagRepository.findTagsByProblemId(problemId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Tag::getId)
                .containsExactlyInAnyOrder(tag1.getId(), tag2.getId());
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
