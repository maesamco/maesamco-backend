package com.maesamco.content.integration;

import com.maesamco.content.application.command.ProblemCreateCommand;
import com.maesamco.content.application.command.ProblemUpdateCommand;
import com.maesamco.content.application.command.UpdateField;
import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.finder_service.LessonFinderService;
import com.maesamco.content.application.finder_service.ProblemFinderService;
import com.maesamco.content.application.persistence_service.ProblemService;
import com.maesamco.content.application.query.ProblemSearchQuery;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.entity.problem.*;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.config.QuerydslConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.infrastructure.persistence.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema",
        "spring.data.jpa.repositories.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        JpaAuditingConfig.class,
        QuerydslConfig.class,
        ProblemService.class,
        ProblemFinderService.class,
        LessonFinderService.class,
        ProblemCommandRepositoryImpl.class,
        ProblemQueryRepositoryImpl.class,
        ProblemVersionRepositoryImpl.class,
        LessonRepositoryImpl.class,
        UnitRepositoryImpl.class,
        CurriculumRepositoryImpl.class,
        ProblemLessonLinkIntegrationTest.TestConfig.class
})
@EnableJpaRepositories(basePackages = "com.maesamco.content.infrastructure.persistence")
class ProblemLessonLinkIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        postgres.start();
    }

    @Autowired private EntityManager entityManager;
    @Autowired private ProblemService problemService;

    enum MissingParent { LESSON, UNIT, CURRICULUM }

    @ParameterizedTest
    @EnumSource(MissingParent.class)
    void createRejectsDeletedLessonOrParent(MissingParent missingParent) {
        Hierarchy hierarchy = createHierarchy();
        delete(hierarchy, missingParent);

        assertLessonNotFound(() -> problemService.createProblem(createCommand(hierarchy.lessonId())));
    }

    @Test
    void createRejectsUnknownLessonBeforeForeignKeyViolation() {
        assertLessonNotFound(() -> problemService.createProblem(createCommand(UUID.randomUUID())));
    }

    @ParameterizedTest
    @EnumSource(MissingParent.class)
    void updateRejectsDeletedLessonOrParent(MissingParent missingParent) {
        Hierarchy hierarchy = createHierarchy();
        UUID problemId = problemService.createProblem(createCommand(null)).getId();
        delete(hierarchy, missingParent);

        assertLessonNotFound(() -> problemService.updateProblem(
                problemId, updateCommand(problemId, UpdateField.of(hierarchy.lessonId()))));
    }

    @Test
    void updateRejectsUnknownLesson() {
        UUID problemId = problemService.createProblem(createCommand(null)).getId();
        assertLessonNotFound(() -> problemService.updateProblem(
                problemId, updateCommand(problemId, UpdateField.of(UUID.randomUUID()))));
    }

    @Test
    void deletedLessonKeepsAlreadyLinkedProblemAndAllowsOtherEditsOrUnlink() {
        Hierarchy hierarchy = createHierarchy();
        UUID problemId = problemService.createProblem(createCommand(hierarchy.lessonId())).getId();
        delete(hierarchy, MissingParent.LESSON);

        assertThat(problemService.getProblemForAdmin(problemId).getLessonId())
                .isEqualTo(hierarchy.lessonId());
        // Problem은 Lesson 삭제와 독립적인 자산이다. 관리자 검색의 lessonId 필터도 유지한다.
        assertThat(problemService.searchProblemsForAdmin(
                new ProblemSearchQuery(null, null, null, null, null, hierarchy.lessonId()),
                PageQuery.of(0, 10)).totalElements()).isEqualTo(1);

        problemService.updateProblem(problemId, updateCommand(problemId, UpdateField.undefined()));
        assertThat(problemService.getProblemForAdmin(problemId).getLessonId())
                .isEqualTo(hierarchy.lessonId());

        problemService.updateProblem(problemId, updateCommand(problemId, UpdateField.of(null)));
        assertThat(problemService.getProblemForAdmin(problemId).getLessonId()).isNull();
    }

    private Hierarchy createHierarchy() {
        Curriculum curriculum = Curriculum.create("Java", ProgrammingLanguage.JAVA);
        entityManager.persist(curriculum);
        Unit unit = Unit.create(curriculum.getId(), "기초", ProgrammingLanguage.JAVA, 1);
        entityManager.persist(unit);
        Lesson lesson = Lesson.create(unit.getId(), "변수", "설명", "본문", ProgrammingLanguage.JAVA, 1);
        entityManager.persist(lesson);
        entityManager.flush();
        return new Hierarchy(curriculum, unit, lesson);
    }

    private void delete(Hierarchy hierarchy, MissingParent missingParent) {
        switch (missingParent) {
            case LESSON -> hierarchy.lesson().softDelete(UUID.randomUUID());
            case UNIT -> hierarchy.unit().softDelete(UUID.randomUUID());
            case CURRICULUM -> hierarchy.curriculum().softDelete(UUID.randomUUID());
        }
        entityManager.flush();
        entityManager.clear();
    }

    private ProblemCreateCommand createCommand(UUID lessonId) {
        return new ProblemCreateCommand("두 수의 합", ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY, ProblemType.CODE, "설명", "public class Main {}",
                RunningTimeLimit.SECOND_1, RunningMemoryLimit.MB_128,
                TimerPolicy.APPLY60, ProblemSource.HUMAN_AUTHORED, lessonId);
    }

    private ProblemUpdateCommand updateCommand(UUID problemId, UpdateField<UUID> lessonId) {
        Long lockVersion = entityManager.find(Problem.class, problemId).getLockVersion();
        return new ProblemUpdateCommand(null, lockVersion, null, null, null, null,
                UpdateField.undefined(), null, null, null, null, lessonId);
    }

    private static void assertLessonNotFound(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LESSON_NOT_FOUND));
    }

    private record Hierarchy(Curriculum curriculum, Unit unit, Lesson lesson) {
        UUID lessonId() { return lesson.getId(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        ProblemPublicationFacade problemPublicationFacade() {
            return mock(ProblemPublicationFacade.class);
        }
    }
}
